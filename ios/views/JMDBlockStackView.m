#import "JMDBlockStackView.h"

#import "JMDBlockTextView.h"
#import "JMDBoxViews.h"
#import "JMDImageView.h"
#import "JMDTableView.h"

@implementation JMDBlockStackView {
  NSArray<JMDMeasuredBlock *> *_measured;
  CGFloat _gap;
}

- (void)setBlocks:(NSArray<JMDMeasuredBlock *> *)blocks gap:(CGFloat)gap {
  _measured = blocks;
  _gap = gap;

  for (UIView *subview in [self.subviews copy]) {
    [subview removeFromSuperview];
  }
  for (JMDMeasuredBlock *measured in blocks) {
    [self addSubview:[self createViewFor:measured]];
  }
  [self setNeedsLayout];
}

- (UIView *)createViewFor:(JMDMeasuredBlock *)measured {
  switch (measured.block.kind) {
    case JMDBlockKindText: {
      JMDBlockTextView *view = [[JMDBlockTextView alloc] initWithFrame:CGRectZero];
      view.host = self.host;
      view.spoilerColor = measured.block.spoilerColor;
      view.spoilerRadius = measured.block.spoilerRadius;
      view.spoilerContinuous = measured.block.spoilerContinuous;
      view.attributedText = measured.block.attributedText;
      return view;
    }
    case JMDBlockKindCode: {
      JMDCodeBlockView *view = [[JMDCodeBlockView alloc] initWithFrame:CGRectZero];
      [view bind:measured];
      return view;
    }
    case JMDBlockKindQuote: {
      JMDQuoteView *view = [[JMDQuoteView alloc] initWithFrame:CGRectZero];
      [view bind:measured gap:_gap host:self.host];
      return view;
    }
    case JMDBlockKindList: {
      JMDListBlockView *view = [[JMDListBlockView alloc] initWithFrame:CGRectZero];
      [view bind:measured gap:_gap host:self.host];
      return view;
    }
    case JMDBlockKindDivider: {
      UIView *view = [[UIView alloc] initWithFrame:CGRectZero];
      view.backgroundColor = measured.block.dividerColor;
      return view;
    }
    case JMDBlockKindImage: {
      JMDImageView *view = [[JMDImageView alloc] initWithFrame:CGRectZero];
      view.host = self.host;
      [view bind:measured.block];
      return view;
    }
    case JMDBlockKindTable: {
      JMDTableView *view = [[JMDTableView alloc] initWithFrame:CGRectZero];
      [view bind:measured host:self.host];
      return view;
    }
  }
  return [[UIView alloc] initWithFrame:CGRectZero];
}


// Never the hit view itself: markdown touches belong to the host component
// view; only nested scrollers (code blocks, tables) claim touches.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
  UIView *hit = [super hitTest:point withEvent:event];
  return hit == self ? nil : hit;
}

- (void)layoutSubviews {
  [super layoutSubviews];
  const CGFloat width = self.bounds.size.width;
  CGFloat y = 0;
  for (NSUInteger i = 0; i < _measured.count && i < self.subviews.count; i++) {
    JMDMeasuredBlock *measured = _measured[i];
    const CGFloat childWidth = measured.block.kind == JMDBlockKindImage
        ? MIN(measured.contentWidth, width)
        : width;
    self.subviews[i].frame = CGRectMake(0, y, childWidth, measured.height);
    y += measured.height;
    if (i + 1 < _measured.count) {
      y += _gap;
    }
  }
}

@end
