#import "JMDBlockTextView.h"

#import <CoreText/CoreText.h>

@implementation JMDBlockTextView {
  NSLayoutManager *_layoutManager;
  NSTextContainer *_textContainer;
  NSTextStorage *_textStorage;
  NSMutableSet<NSNumber *> *_hiddenSpoilers;
}

- (instancetype)initWithFrame:(CGRect)frame {
  if (self = [super initWithFrame:frame]) {
    self.backgroundColor = UIColor.clearColor;
    self.contentMode = UIViewContentModeRedraw;
  }
  return self;
}

// Hit-test transparent: the host component view owns all touch handling,
// so ancestor scroll views treat markdown content like any React view.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
  return nil;
}

- (void)setAttributedText:(NSAttributedString *)attributedText {
  if (![_attributedText isEqualToAttributedString:attributedText]) {
    _attributedText = [attributedText copy];
    _layoutManager = nil;
    // The hiding state lives in the storage, which is rebuilt (with the
    // original colors) alongside the layout manager.
    [_hiddenSpoilers removeAllObjects];
    self.isAccessibilityElement = YES;
    self.accessibilityLabel = attributedText.string;
    self.accessibilityTraits = UIAccessibilityTraitStaticText;
    [self setNeedsDisplay];
  }
}

// Lazy TextKit stack shared by drawing, overlay geometry, and hit-testing
// (and configured identically to the measurer's) — one engine, so they can
// never disagree about line positions.
- (NSLayoutManager *)layoutManagerForBounds {
  if (_layoutManager == nil && _attributedText != nil) {
    _textStorage = [[NSTextStorage alloc] initWithAttributedString:_attributedText];
    _layoutManager = [NSLayoutManager new];
    _textContainer =
        [[NSTextContainer alloc] initWithSize:CGSizeMake(self.bounds.size.width, CGFLOAT_MAX)];
    _textContainer.lineFragmentPadding = 0;
    [_layoutManager addTextContainer:_textContainer];
    [_textStorage addLayoutManager:_layoutManager];
  } else if (_textContainer != nil &&
             _textContainer.size.width != self.bounds.size.width) {
    _textContainer.size = CGSizeMake(self.bounds.size.width, CGFLOAT_MAX);
  }
  return _layoutManager;
}

- (void)drawRect:(CGRect)rect {
  NSLayoutManager *layoutManager = [self layoutManagerForBounds];
  if (layoutManager == nil) {
    return;
  }
  // One engine for everything: the same layout manager that positions the
  // overlays also draws the glyphs. NSStringDrawing typesets separately and
  // disagrees with NSLayoutManager about font leading under a lineHeight
  // cap (fonts with a nonzero line gap drift ~gap pt per line), which
  // misaligned overlays on wrapped lines.
  [self syncSpoilerHiding:layoutManager];
  [self drawRunBackgrounds:layoutManager];
  const NSRange glyphRange =
      [layoutManager glyphRangeForTextContainer:self->_textContainer];
  [layoutManager drawBackgroundForGlyphRange:glyphRange atPoint:CGPointZero];
  [layoutManager drawGlyphsForGlyphRange:glyphRange atPoint:CGPointZero];
  [self drawSpoilerCovers:layoutManager];
}

// Unrevealed spoiler text draws fully transparent — the cover hugs the
// text, so glyphs would leak around it if drawn. Colors mutate on the
// shared storage (color-only edits don't reflow); originals restore from
// the immutable _attributedText on reveal.
- (void)syncSpoilerHiding:(NSLayoutManager *)layoutManager {
  if (self.host == nil || _attributedText.length == 0 || _textStorage == nil) {
    return;
  }
  if (_hiddenSpoilers == nil) {
    _hiddenSpoilers = [NSMutableSet new];
  }
  [_textStorage beginEditing];
  [_attributedText
      enumerateAttribute:JMDSpoilerIDAttributeName
                 inRange:NSMakeRange(0, _attributedText.length)
                 options:0
              usingBlock:^(NSNumber *spoilerId, NSRange range, BOOL *stop) {
                if (spoilerId == nil) {
                  return;
                }
                const BOOL shouldHide =
                    ![self.host isSpoilerRevealed:spoilerId.integerValue];
                const BOOL isHidden =
                    [self->_hiddenSpoilers containsObject:spoilerId];
                if (shouldHide == isHidden) {
                  return;
                }
                if (shouldHide) {
                  [self->_textStorage addAttributes:@{
                    NSForegroundColorAttributeName : UIColor.clearColor,
                    NSUnderlineColorAttributeName : UIColor.clearColor,
                    NSStrikethroughColorAttributeName : UIColor.clearColor,
                  }
                                              range:range];
                  [self->_hiddenSpoilers addObject:spoilerId];
                } else {
                  [self->_attributedText
                      enumerateAttributesInRange:range
                                         options:0
                                      usingBlock:^(NSDictionary *attrs,
                                                   NSRange subRange,
                                                   BOOL *stopInner) {
                                        [self->_textStorage
                                            setAttributes:attrs
                                                    range:subRange];
                                      }];
                  [self->_hiddenSpoilers removeObject:spoilerId];
                }
              }];
  [_textStorage endEditing];
}

// A chip inside an unrevealed spoiler would peek around the cover.
- (BOOL)isRangeInsideHiddenSpoiler:(NSRange)range {
  if (self.host == nil) {
    return NO;
  }
  __block BOOL hidden = NO;
  [_attributedText
      enumerateAttribute:JMDSpoilerIDAttributeName
                 inRange:range
                 options:0
              usingBlock:^(NSNumber *spoilerId, NSRange subRange, BOOL *stop) {
                if (spoilerId != nil &&
                    ![self.host isSpoilerRevealed:spoilerId.integerValue]) {
                  hidden = YES;
                  *stop = YES;
                }
              }];
  return hidden;
}

// Approximates iOS's continuous ("squircle") corner curve for a single
// rect; falls back to a plain rounded rect when the radius dominates.
static UIBezierPath *JMDChipPath(CGRect rect, CGFloat radius, BOOL continuous) {
  radius = MIN(radius, MIN(rect.size.width, rect.size.height) / 2);
  if (radius <= 0) {
    return [UIBezierPath bezierPathWithRect:rect];
  }
  if (!continuous) {
    return [UIBezierPath bezierPathWithRoundedRect:rect cornerRadius:radius];
  }
  // The standard smooth-corner approximation: control points extend ~1.528x
  // the radius along the edges. Degrades to circular when the rect is too
  // small to fit the extended corners.
  const CGFloat k = 1.528665;
  const CGFloat ext = radius * k;
  if (rect.size.width < 2 * ext || rect.size.height < 2 * ext) {
    return [UIBezierPath bezierPathWithRoundedRect:rect cornerRadius:radius];
  }
  const CGFloat minX = CGRectGetMinX(rect), maxX = CGRectGetMaxX(rect);
  const CGFloat minY = CGRectGetMinY(rect), maxY = CGRectGetMaxY(rect);
  UIBezierPath *path = [UIBezierPath bezierPath];
  [path moveToPoint:CGPointMake(minX + ext, minY)];
  [path addLineToPoint:CGPointMake(maxX - ext, minY)];
  [path addCurveToPoint:CGPointMake(maxX, minY + ext)
          controlPoint1:CGPointMake(maxX - ext + radius, minY)
          controlPoint2:CGPointMake(maxX, minY + ext - radius)];
  [path addLineToPoint:CGPointMake(maxX, maxY - ext)];
  [path addCurveToPoint:CGPointMake(maxX - ext, maxY)
          controlPoint1:CGPointMake(maxX, maxY - ext + radius)
          controlPoint2:CGPointMake(maxX - ext + radius, maxY)];
  [path addLineToPoint:CGPointMake(minX + ext, maxY)];
  [path addCurveToPoint:CGPointMake(minX, maxY - ext)
          controlPoint1:CGPointMake(minX + ext - radius, maxY)
          controlPoint2:CGPointMake(minX, maxY - ext + radius)];
  [path addLineToPoint:CGPointMake(minX, minY + ext)];
  [path addCurveToPoint:CGPointMake(minX + ext, minY)
          controlPoint1:CGPointMake(minX, minY + ext - radius)
          controlPoint2:CGPointMake(minX + ext - radius, minY)];
  [path closePath];
  return path;
}

// Overlays hug the text. lineHeight moves line boxes around the text, so
// any box derived from them inherits that skew; these rects anchor on the
// drawn baseline instead. Horizontal comes from the segment's glyph ink;
// vertical is the FONT's ink envelope (cap height above the baseline,
// descender depth below) so every run of a font renders the same height
// whether or not its particular glyphs have capitals or descenders.
static const CGFloat JMDInkPad = 2;

// Per-line overlay rects for a character range. Whitespace-only segments
// have no ink and produce no rect.
- (NSArray<NSValue *> *)inkRectsForRange:(NSRange)range
                           layoutManager:(NSLayoutManager *)layoutManager
                                 padLeft:(CGFloat)padLeft
                                padRight:(CGFloat)padRight {
  CGContextRef context = UIGraphicsGetCurrentContext();
  // CTLineGetImageBounds reports bounds relative to the context's current
  // text position, and NSStringDrawing leaves it wherever the last drawn
  // line ended.
  CGContextSetTextPosition(context, 0, 0);
  const CGFloat maxWidth = self.bounds.size.width;
  const CGFloat maxHeight = self.bounds.size.height;
  const NSRange glyphRange =
      [layoutManager glyphRangeForCharacterRange:range actualCharacterRange:nil];
  NSMutableArray<NSValue *> *lineRects = [NSMutableArray new];
  NSUInteger glyphIndex = glyphRange.location;
  while (glyphIndex < NSMaxRange(glyphRange)) {
    NSRange lineGlyphRange;
    const CGRect fragment =
        [layoutManager lineFragmentRectForGlyphAtIndex:glyphIndex
                                        effectiveRange:&lineGlyphRange];
    const NSRange lineRange = NSIntersectionRange(lineGlyphRange, glyphRange);
    if (lineRange.length == 0) {
      break;
    }
    const NSRange charRange =
        [layoutManager characterRangeForGlyphRange:lineRange actualGlyphRange:nil];
    const CGPoint startLocation =
        [layoutManager locationForGlyphAtIndex:lineRange.location];
    // The typesetter already folds NSBaselineOffset (lineHeight centering,
    // sup/sub shifts) into the glyph location.
    const CGFloat baselineY = fragment.origin.y + startLocation.y;
    const CGFloat penX = fragment.origin.x + startLocation.x;

    CTLineRef line = CTLineCreateWithAttributedString(
        (__bridge CFAttributedStringRef)[self->_attributedText
            attributedSubstringFromRange:charRange]);
    const CGRect ink = CTLineGetImageBounds(line, context);
    CFRelease(line);
    if (!CGRectIsNull(ink) && ink.size.width > 0) {
      UIFont *font =
          [self->_attributedText attribute:NSFontAttributeName
                                   atIndex:charRange.location
                            effectiveRange:nil]
              ?: [UIFont systemFontOfSize:UIFont.systemFontSize];
      const CGFloat top = MAX(baselineY - font.capHeight - JMDInkPad, 0);
      const CGFloat bottom =
          MIN(baselineY - font.descender + JMDInkPad, maxHeight);
      const CGFloat left = MAX(penX + CGRectGetMinX(ink) - padLeft, 0);
      const CGFloat right = MIN(penX + CGRectGetMaxX(ink) + padRight, maxWidth);
      if (right > left && bottom > top) {
        [lineRects addObject:[NSValue valueWithCGRect:CGRectMake(
                                                          left, top,
                                                          right - left,
                                                          bottom - top)]];
      }
    }
    glyphIndex = NSMaxRange(lineGlyphRange);
  }
  return lineRects;
}

// Run background chips (inlineCode/link/mention and plain highlights),
// drawn UNDER the text; enumerates the display copy so chips hidden with
// their spoiler don't draw.
- (void)drawRunBackgrounds:(NSLayoutManager *)layoutManager {
  if (_attributedText.length == 0) {
    return;
  }
  [_attributedText
      enumerateAttribute:JMDRunBackgroundAttributeName
                 inRange:NSMakeRange(0, _attributedText.length)
                 options:0
              usingBlock:^(JMDRunBackground *chip, NSRange range, BOOL *stop) {
                if (chip == nil || [self isRangeInsideHiddenSpoiler:range]) {
                  return;
                }
                NSArray<NSValue *> *rects = [self
                    inkRectsForRange:range
                       layoutManager:layoutManager
                             padLeft:chip.padLeft > 0 ? chip.padLeft : JMDInkPad
                            padRight:chip.padRight > 0 ? chip.padRight
                                                       : JMDInkPad];
                [chip.color setFill];
                for (NSValue *value in rects) {
                  [JMDChipPath(value.CGRectValue, chip.radius,
                               chip.continuousCurve) fill];
                }
              }];
}

// Spoiler cover chips, drawn OVER the (transparent) text until revealed.
// Same ink geometry as the run backgrounds so covers and backgrounds look
// identical.
- (void)drawSpoilerCovers:(NSLayoutManager *)layoutManager {
  if (_attributedText.length == 0 || self.host == nil) {
    return;
  }
  [_attributedText
      enumerateAttribute:JMDSpoilerIDAttributeName
                 inRange:NSMakeRange(0, _attributedText.length)
                 options:0
              usingBlock:^(NSNumber *spoilerId, NSRange range, BOOL *stop) {
                if (spoilerId == nil ||
                    [self.host isSpoilerRevealed:spoilerId.integerValue]) {
                  return;
                }
                NSArray<NSValue *> *rects =
                    [self inkRectsForRange:range
                             layoutManager:layoutManager
                                   padLeft:JMDInkPad
                                  padRight:JMDInkPad];
                [self.spoilerColor ?: UIColor.darkGrayColor setFill];
                for (NSValue *value in rects) {
                  [JMDChipPath(value.CGRectValue, self.spoilerRadius,
                               self.spoilerContinuous) fill];
                }
              }];
}

- (nullable NSDictionary *)attributesAtPoint:(CGPoint)point {
  NSLayoutManager *layoutManager = [self layoutManagerForBounds];
  if (layoutManager == nil || _attributedText.length == 0) {
    return nil;
  }
  const NSUInteger glyphIndex = [layoutManager glyphIndexForPoint:point
                                                  inTextContainer:_textContainer];
  const CGRect glyphRect = [layoutManager boundingRectForGlyphRange:NSMakeRange(glyphIndex, 1)
                                                    inTextContainer:_textContainer];
  if (!CGRectContainsPoint(CGRectInset(glyphRect, -8, -4), point)) {
    return nil;
  }
  const NSUInteger charIndex = [layoutManager characterIndexForGlyphAtIndex:glyphIndex];
  if (charIndex >= _attributedText.length) {
    return nil;
  }
  return [_attributedText attributesAtIndex:charIndex effectiveRange:nil];
}

@end
