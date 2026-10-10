#import "JMDImageView.h"

#import <SDWebImage/SDWebImage.h>

#import "JMDBlockTextView.h"

@implementation JMDImageView {
  JMDBlock *_block;
  SDAnimatedImageView *_imageView;
  JMDBlockTextView *_captionView;
  CGFloat _captionHeight;
}

- (instancetype)initWithFrame:(CGRect)frame {
  if (self = [super initWithFrame:frame]) {
    // Animated formats (GIF/APNG) play with lazy per-frame decoding and a
    // dynamic frame-buffer cap, so long GIFs in image-heavy feeds do not
    // balloon memory.
    _imageView = [[SDAnimatedImageView alloc] initWithFrame:CGRectZero];
    _imageView.contentMode = UIViewContentModeScaleAspectFit;
    _imageView.clearBufferWhenStopped = YES;
    [self addSubview:_imageView];
  }
  return self;
}

// Hit-test transparent: the host component view owns all touch handling.
- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event {
  return nil;
}

- (nullable NSString *)imageUrl {
  return _block.imageUrl;
}

- (UIView *)mediaView {
  return _imageView;
}

- (void)bind:(JMDMeasuredBlock *)measured {
  JMDBlock *block = measured.block;
  _block = block;
  _imageView.backgroundColor = block.mediaBackground ?: UIColor.clearColor;
  _imageView.layer.cornerRadius = block.mediaBorderRadius;
  _imageView.layer.masksToBounds = block.mediaBorderRadius > 0;

  if (measured.textStorage != nil) {
    if (_captionView == nil) {
      _captionView = [[JMDBlockTextView alloc] initWithFrame:CGRectZero];
      // Skipped by the host's hit walk, so caption taps open the image.
      _captionView.userInteractionEnabled = NO;
      [self addSubview:_captionView];
    }
    _captionView.hidden = NO;
    [_captionView bindTextStorage:measured.textStorage];
    _captionHeight = block.captionMarginTop + measured.textHeight;
  } else {
    _captionView.hidden = YES;
    _captionHeight = 0;
  }
  [self setNeedsLayout];

  NSString *url = block.imageUrl ?: @"";
  if (url.length == 0) {
    [_imageView sd_cancelCurrentImageLoad];
    _imageView.image = nil;
    return;
  }

  __weak JMDImageView *weakSelf = self;
  // sd_setImage cancels this view's previous request (recycling) and shares
  // in-flight downloads by URL; completion runs synchronously on memory
  // cache hits, so a fresh view still resizes un-presized images.
  [_imageView sd_setImageWithURL:[NSURL URLWithString:url]
                placeholderImage:nil
                         options:SDWebImageRetryFailed | SDWebImageScaleDownLargeImages
                       completed:^(UIImage *image, NSError *error, SDImageCacheType cacheType,
                                   NSURL *imageURL) {
                         JMDImageView *strongSelf = weakSelf;
                         if (strongSelf == nil || image == nil ||
                             ![strongSelf->_block.imageUrl isEqualToString:url]) {
                           return;
                         }
                         [strongSelf.host mediaIntrinsicSize:image.size forKey:url];
                       }];
}

- (void)layoutSubviews {
  [super layoutSubviews];
  const CGSize size = self.bounds.size;
  _imageView.frame = CGRectMake(0, 0, size.width, MAX(size.height - _captionHeight, 0));
  _captionView.frame = CGRectMake(
      0, CGRectGetMaxY(_imageView.frame) + _block.captionMarginTop, size.width,
      MAX(_captionHeight - _block.captionMarginTop, 0));
}

@end
