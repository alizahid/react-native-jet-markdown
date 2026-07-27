#import "FastMarkdownView.h"

#import <React/RCTConversions.h>

#import <react/renderer/components/FastMarkdownViewSpec/EventEmitters.h>
#import <react/renderer/components/FastMarkdownViewSpec/Props.h>
#import <react/renderer/core/ConcreteComponentDescriptor.h>

// Imported directly (not via the override ComponentDescriptors.h) so the
// custom measurable shadow node is used regardless of header search order.
#import "react/FastMarkdownShadowNode.h"

namespace facebook::react {
using FMDComponentDescriptor = ConcreteComponentDescriptor<FastMarkdownShadowNode>;
} // namespace facebook::react

#import "RCTFabricComponentsPlugins.h"

#import "measure/FMDMarkdownMeasurer.h"
#import "render/FMDContentCache.h"
#import "style/FMDFontScale.h"
#import "style/FMDStyleConfig.h"
#import "views/FMDBlockStackView.h"
#import "views/FMDBlockTextView.h"
#import "views/FMDImageView.h"
#import "views/FMDMarkdownHost.h"

#import "react/FastMarkdownMeasurer.h"

using namespace facebook::react;

@interface FastMarkdownView () <FMDMarkdownHost, UIGestureRecognizerDelegate>
@end

static void FMDSetNeedsDisplayDeep(UIView *view);
static CGRect FMDScreenFrameOfView(UIView *view);

@implementation FastMarkdownView {
  NSString *_markdown;
  NSString *_stylesJson;
  BOOL _allowFontScaling;
  FMDBlockStackView *_stack;
  NSString *_boundKey;
  CGFloat _boundWidth;
  // url -> @[w, h] points: from the images prop (wins) and loaded bitmaps.
  NSMutableDictionary<NSString *, NSArray<NSNumber *> *> *_propImageSizes;
  NSMutableDictionary<NSString *, NSArray<NSNumber *> *> *_loadedImageSizes;
  NSMutableSet<NSNumber *> *_revealedSpoilers;
  FastMarkdownShadowNode::ConcreteState::Shared _state;
}

+ (void)load {
  fastmarkdown::FastMarkdownMeasurer::shared().install(
      [](const std::string &markdown,
         const std::string &stylesJson,
         const std::string &imagesJson,
         float maxWidth,
         float fontScale) -> float {
        NSString *markdownString =
            [[NSString alloc] initWithBytes:markdown.data()
                                     length:markdown.size()
                                   encoding:NSUTF8StringEncoding];
        NSString *stylesString =
            [[NSString alloc] initWithBytes:stylesJson.data()
                                     length:stylesJson.size()
                                   encoding:NSUTF8StringEncoding];
        NSString *imagesString =
            [[NSString alloc] initWithBytes:imagesJson.data()
                                     length:imagesJson.size()
                                   encoding:NSUTF8StringEncoding];
        return [FMDMarkdownMeasurer measureMarkdown:markdownString ?: @""
                                         stylesJson:stylesString ?: @""
                                         imagesJson:imagesString ?: @""
                                           maxWidth:maxWidth
                                          fontScale:fontScale];
      });
}

+ (ComponentDescriptorProvider)componentDescriptorProvider {
  return concreteComponentDescriptorProvider<FMDComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame {
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const FastMarkdownViewProps>();
    _props = defaultProps;
    _markdown = @"";
    _stylesJson = @"";
    _allowFontScaling = YES;

    // Dynamic Type changes re-render in place; RN relayouts the shadow
    // tree with the new multiplier on its own.
    [NSNotificationCenter.defaultCenter
        addObserver:self
           selector:@selector(fmdContentSizeCategoryDidChange)
               name:UIContentSizeCategoryDidChangeNotification
             object:nil];
    _stack = [[FMDBlockStackView alloc] initWithFrame:CGRectZero];
    _propImageSizes = [NSMutableDictionary new];
    _loadedImageSizes = [NSMutableDictionary new];
    _revealedSpoilers = [NSMutableSet new];
    _stack.host = self;
    [self addSubview:_stack];

    // The internal tree is hit-test transparent; the component view owns
    // link/mention/spoiler/image touch handling (like React Native's Text).
    UITapGestureRecognizer *tap =
        [[UITapGestureRecognizer alloc] initWithTarget:self action:@selector(fmdHandleTap:)];
    UILongPressGestureRecognizer *longPress =
        [[UILongPressGestureRecognizer alloc] initWithTarget:self
                                                      action:@selector(fmdHandleLongPress:)];
    tap.delegate = self;
    longPress.delegate = self;
    [self addGestureRecognizer:tap];
    [self addGestureRecognizer:longPress];
  }
  return self;
}

#pragma mark - Interaction resolution

// Deepest content view at a host-space point (internal views are hit-test
// transparent, so this walks frames directly). Views that opt out of user
// interaction (border overlays) are skipped.
- (UIView *)fmdContentViewAtPoint:(CGPoint)point inView:(UIView *)view {
  for (UIView *subview in [view.subviews reverseObjectEnumerator]) {
    if (subview.hidden || subview.alpha < 0.01 || !subview.userInteractionEnabled) {
      continue;
    }
    const CGPoint local = [view convertPoint:point toView:subview];
    if ([subview pointInside:local withEvent:nil]) {
      return [self fmdContentViewAtPoint:local inView:subview];
    }
  }
  return view;
}

- (nullable UIView *)fmdInteractiveViewAtPoint:(CGPoint)point
                                    localPoint:(CGPoint *)localPoint {
  UIView *view = [self fmdContentViewAtPoint:point inView:self];
  while (view != nil && view != self) {
    if ([view isKindOfClass:[FMDBlockTextView class]] ||
        [view isKindOfClass:[FMDImageView class]]) {
      if (localPoint != nil) {
        *localPoint = [self convertPoint:point toView:view];
      }
      return view;
    }
    view = view.superview;
  }
  return nil;
}

- (BOOL)fmdIsInteractiveAtPoint:(CGPoint)point {
  CGPoint local;
  UIView *view = [self fmdInteractiveViewAtPoint:point localPoint:&local];
  if ([view isKindOfClass:[FMDImageView class]]) {
    return ((FMDImageView *)view).imageUrl != nil;
  }
  if ([view isKindOfClass:[FMDBlockTextView class]]) {
    NSDictionary *attributes = [(FMDBlockTextView *)view attributesAtPoint:local];
    return attributes[FMDLinkURLAttributeName] != nil ||
        attributes[FMDSpoilerIDAttributeName] != nil;
  }
  return NO;
}

// Touches that do not start on an interactive range are never tracked, so
// they cannot interfere with an ancestor scroll view's pan.
- (BOOL)gestureRecognizer:(UIGestureRecognizer *)recognizer
       shouldReceiveTouch:(UITouch *)touch {
  return [self fmdIsInteractiveAtPoint:[touch locationInView:self]];
}

// Non-interactive points fall through to ancestors so a wrapping pressable
// becomes the hit view. UIControl-based wrappers (react-native-gesture-handler's
// button) need the touch delivered to them directly and never fire while this
// view claims every point. Interactive points stay claimed for the host
// recognizers; nested code/table scroll views are returned by super unchanged.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
  UIView *result = [super hitTest:point withEvent:event];
  if (result == self && ![self fmdIsInteractiveAtPoint:point]) {
    return nil;
  }
  return result;
}

// Scrolling always wins: a drag that begins on a link still pans the list
// (the tap/long-press simply fail on movement).
- (BOOL)gestureRecognizer:(UIGestureRecognizer *)recognizer
    shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)other {
  return [other.view isKindOfClass:[UIScrollView class]];
}

- (void)fmdHandleTap:(UITapGestureRecognizer *)recognizer {
  CGPoint local;
  UIView *view = [self fmdInteractiveViewAtPoint:[recognizer locationInView:self]
                                      localPoint:&local];
  if ([view isKindOfClass:[FMDImageView class]]) {
    NSString *url = ((FMDImageView *)view).imageUrl;
    if (url != nil) {
      [self imagePressed:url frame:FMDScreenFrameOfView(view)];
    }
    return;
  }
  if (![view isKindOfClass:[FMDBlockTextView class]]) {
    return;
  }
  NSDictionary *attributes = [(FMDBlockTextView *)view attributesAtPoint:local];
  NSNumber *spoilerId = attributes[FMDSpoilerIDAttributeName];
  NSString *url = attributes[FMDLinkURLAttributeName];

  if (spoilerId != nil && ![self isSpoilerRevealed:spoilerId.integerValue]) {
    // First tap reveals; links inside come alive afterwards.
    [self toggleSpoiler:spoilerId.integerValue];
  } else if (url != nil) {
    [self linkPressed:url];
  } else if (spoilerId != nil) {
    [self toggleSpoiler:spoilerId.integerValue];
  }
}

- (void)fmdHandleLongPress:(UILongPressGestureRecognizer *)recognizer {
  if (recognizer.state != UIGestureRecognizerStateBegan) {
    return;
  }
  CGPoint local;
  UIView *view = [self fmdInteractiveViewAtPoint:[recognizer locationInView:self]
                                      localPoint:&local];
  if (![view isKindOfClass:[FMDBlockTextView class]]) {
    return;
  }
  NSDictionary *attributes = [(FMDBlockTextView *)view attributesAtPoint:local];
  NSNumber *spoilerId = attributes[FMDSpoilerIDAttributeName];
  NSString *url = attributes[FMDLinkURLAttributeName];
  if (url != nil && (spoilerId == nil || [self isSpoilerRevealed:spoilerId.integerValue])) {
    [self linkLongPressed:url];
  }
}

#pragma mark - FMDMarkdownHost

- (void)imageIntrinsicSize:(CGSize)size forUrl:(NSString *)url {
  [self noteIntrinsicSize:size forUrl:url];
}

- (BOOL)isSpoilerRevealed:(NSInteger)spoilerId {
  return [_revealedSpoilers containsObject:@(spoilerId)];
}

- (void)toggleSpoiler:(NSInteger)spoilerId {
  if ([_revealedSpoilers containsObject:@(spoilerId)]) {
    [_revealedSpoilers removeObject:@(spoilerId)];
  } else {
    [_revealedSpoilers addObject:@(spoilerId)];
  }
  FMDSetNeedsDisplayDeep(self);
}

- (const FastMarkdownViewEventEmitter *)markdownEventEmitter {
  if (!_eventEmitter) {
    return nullptr;
  }
  return static_cast<const FastMarkdownViewEventEmitter *>(_eventEmitter.get());
}

- (void)linkPressed:(NSString *)url {
  if (const auto *emitter = [self markdownEventEmitter]) {
    emitter->onLinkPress({.url = std::string(url.UTF8String ?: "")});
  }
}

- (void)linkLongPressed:(NSString *)url {
  if (const auto *emitter = [self markdownEventEmitter]) {
    emitter->onLinkLongPress({.url = std::string(url.UTF8String ?: "")});
  }
}

- (void)imagePressed:(NSString *)url frame:(CGRect)frame {
  if (const auto *emitter = [self markdownEventEmitter]) {
    emitter->onImagePress({
        .height = frame.size.height,
        .url = std::string(url.UTF8String ?: ""),
        .width = frame.size.width,
        .x = frame.origin.x,
        .y = frame.origin.y,
    });
  }
}

static CGRect FMDScreenFrameOfView(UIView *view) {
  CGRect frame = [view convertRect:view.bounds toView:nil];
  UIWindow *window = view.window;
  if (window != nil) {
    frame = [window convertRect:frame toCoordinateSpace:window.screen.coordinateSpace];
  }
  return frame;
}

static void FMDSetNeedsDisplayDeep(UIView *view) {
  // Spoiler covers are drawn by the text views only; invalidating images
  // and scrollers too would double the redraw work.
  if ([view isKindOfClass:FMDBlockTextView.class]) {
    [view setNeedsDisplay];
  }
  for (UIView *subview in view.subviews) {
    FMDSetNeedsDisplayDeep(subview);
  }
}

- (void)traitCollectionDidChange:(UITraitCollection *)previousTraitCollection {
  [super traitCollectionDidChange:previousTraitCollection];
  if ([self.traitCollection
          hasDifferentColorAppearanceComparedToTraitCollection:previousTraitCollection]) {
    // Dynamic (platform) colors resolve at draw time for text, but border
    // and background colors snapshot into CGColors when blocks bind; rebind
    // so they re-resolve under the new appearance.
    _boundKey = nil;
    [self setNeedsLayout];
    FMDSetNeedsDisplayDeep(self);
  }
}

- (void)noteIntrinsicSize:(CGSize)size forUrl:(NSString *)url {
  if (_propImageSizes[url] != nil || _loadedImageSizes[url] != nil) {
    return;
  }
  _loadedImageSizes[url] = @[ @(size.width), @(size.height) ];

  // Publish into the shadow-node state so measure() grows the component.
  if (_state != nullptr) {
    std::map<std::string, FastMarkdownState::ImageSize> sizes;
    for (NSString *key in _loadedImageSizes) {
      NSArray<NSNumber *> *value = _loadedImageSizes[key];
      sizes[std::string(key.UTF8String ?: "")] = FastMarkdownState::ImageSize{
          value[0].doubleValue, value[1].doubleValue};
    }
    _state->updateState(FastMarkdownState(std::move(sizes)));
  }
  [self setNeedsLayout];
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState {
  _state = std::static_pointer_cast<const FastMarkdownShadowNode::ConcreteState>(state);
}

- (void)dealloc {
  [NSNotificationCenter.defaultCenter removeObserver:self];
}

- (void)fmdContentSizeCategoryDidChange {
  if (_allowFontScaling) {
    [self setNeedsLayout];
  }
}

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps {
  const auto &newViewProps = *std::static_pointer_cast<FastMarkdownViewProps const>(props);

  NSString *markdown =
      [[NSString alloc] initWithBytes:newViewProps.markdown.data()
                               length:newViewProps.markdown.size()
                             encoding:NSUTF8StringEncoding] ?: @"";
  NSString *stylesJson =
      [[NSString alloc] initWithBytes:newViewProps.stylesJson.data()
                               length:newViewProps.stylesJson.size()
                             encoding:NSUTF8StringEncoding] ?: @"";

  if (![markdown isEqualToString:_markdown]) {
    // Spoiler ids are render-order counters and learned image sizes belong
    // to the old document; both must not survive a content change.
    [_revealedSpoilers removeAllObjects];
    [_loadedImageSizes removeAllObjects];
  }
  if (![markdown isEqualToString:_markdown] || ![stylesJson isEqualToString:_stylesJson] ||
      newViewProps.allowFontScaling != _allowFontScaling) {
    _markdown = markdown;
    _stylesJson = stylesJson;
    _allowFontScaling = newViewProps.allowFontScaling;
    [self setNeedsLayout];
  }

  NSMutableDictionary<NSString *, NSArray<NSNumber *> *> *nextPropSizes =
      [NSMutableDictionary dictionaryWithCapacity:newViewProps.images.size()];
  for (const auto &image : newViewProps.images) {
    NSString *url = [[NSString alloc] initWithBytes:image.url.data()
                                             length:image.url.size()
                                           encoding:NSUTF8StringEncoding];
    if (url != nil) {
      nextPropSizes[url] = @[ @(image.width), @(image.height) ];
    }
  }
  if (![nextPropSizes isEqualToDictionary:_propImageSizes]) {
    _propImageSizes = nextPropSizes;
    // The pre-size data changed: cached layouts for the old sizes must not
    // be reused even when the measured height happens to match.
    [self setNeedsLayout];
  }

  [super updateProps:props oldProps:oldProps];
}

- (NSDictionary<NSString *, NSArray<NSNumber *> *> *)mergedImageSizes {
  if (_loadedImageSizes.count == 0 && _propImageSizes.count == 0) {
    return @{};
  }
  NSMutableDictionary *merged = [_loadedImageSizes mutableCopy];
  [merged addEntriesFromDictionary:_propImageSizes];
  return merged;
}

- (void)layoutSubviews {
  [super layoutSubviews];
  [self rebuildBlocks];
}

- (void)prepareForRecycle {
  [super prepareForRecycle];
  _markdown = @"";
  _stylesJson = @"";
  _boundKey = nil;
  _state = nullptr;
  [_propImageSizes removeAllObjects];
  [_loadedImageSizes removeAllObjects];
  [_revealedSpoilers removeAllObjects];
  [_stack setBlocks:@[] gap:0];
}

- (void)rebuildBlocks {
  FMDStyleConfig *styles = [FMDStyleConfig configWithJson:_stylesJson];
  self.backgroundColor = styles.backgroundColor ?: UIColor.clearColor;

  const CGFloat contentWidth =
      self.bounds.size.width - styles.paddingLeft - styles.paddingRight;
  if (contentWidth <= 0 || _markdown.length == 0) {
    [_stack setBlocks:@[] gap:0];
    _boundKey = nil;
    return;
  }

  // Must match the shadow node's LayoutContext::fontSizeMultiplier.
  const CGFloat fontScale = _allowFontScaling ? FMDFontSizeMultiplier() : 1.0;
  FMDRenderedContent *content = [FMDContentCache contentForMarkdown:_markdown
                                                         stylesJson:_stylesJson
                                                          fontScale:fontScale];
  NSDictionary *imageSizes = [self mergedImageSizes];
  FMDWidthLayout *layout = [content layoutForWidth:contentWidth imageSizes:imageSizes];

  // Full contents, not hashes: CFString samples long strings and
  // NSDictionary.hash is the entry count — both collide.
  NSString *key = [NSString stringWithFormat:@"%@\x1f%@\x1f%@\x1f%.3f",
                                             _markdown,
                                             _stylesJson,
                                             [FMDRenderedContent keyForImageSizes:imageSizes],
                                             fontScale];
  if (![key isEqualToString:_boundKey] || _boundWidth != contentWidth) {
    _boundKey = key;
    _boundWidth = contentWidth;
    [_stack setBlocks:layout.measured gap:content.gap];
  }

  const CGFloat contentHeight =
      layout.totalHeight - styles.paddingTop - styles.paddingBottom;
  _stack.frame =
      CGRectMake(styles.paddingLeft, styles.paddingTop, contentWidth, contentHeight);
}

@end
