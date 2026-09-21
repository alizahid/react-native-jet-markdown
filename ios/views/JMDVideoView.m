#import "JMDVideoView.h"

// Optional native integration: no Nitro/Swift dependency for markdown-only apps.
// JetVideo exports this Objective-C selector on JetVideoInlineView.
@protocol JMDInlineVideo <NSObject>
- (void)configureSource:(NSString *)source poster:(NSString *)poster;
@end

@implementation JMDVideoView {
  UIView<JMDInlineVideo> *_player;
  UIButton *_fallback;
  NSString *_url;
}

- (instancetype)initWithFrame:(CGRect)frame {
  if (self = [super initWithFrame:frame]) {
    self.backgroundColor = UIColor.blackColor;
    self.clipsToBounds = YES;
    Class playerClass = NSClassFromString(@"JetVideoInlineView");
    if ([playerClass isSubclassOfClass:UIView.class] &&
        [playerClass instancesRespondToSelector:@selector(configureSource:poster:)]) {
      _player = [[playerClass alloc] initWithFrame:self.bounds];
      _player.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
      [self addSubview:_player];
    } else {
      _fallback = [UIButton buttonWithType:UIButtonTypeSystem];
      _fallback.frame = self.bounds;
      _fallback.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
      [_fallback setTitle:@"Open video" forState:UIControlStateNormal];
      [_fallback setTitleColor:UIColor.whiteColor forState:UIControlStateNormal];
      [_fallback addTarget:self action:@selector(openVideo) forControlEvents:UIControlEventTouchUpInside];
      [self addSubview:_fallback];
    }
  }
  return self;
}

- (void)bind:(JMDBlock *)block {
  _url = block.videoUrl;
  [_player configureSource:_url ?: @"" poster:block.videoPoster ?: @""];
  _fallback.accessibilityHint = _url;
}

- (void)openVideo {
  if (_url.length > 0) [self.host linkPressed:_url];
}

@end
