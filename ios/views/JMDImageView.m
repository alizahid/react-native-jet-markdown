#import "JMDImageView.h"

#import <SDWebImage/SDWebImage.h>

@implementation JMDImageView {
  JMDBlock *_block;
  SDAnimatedImageView *_imageView;
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

- (void)bind:(JMDBlock *)block {
  _block = block;
  self.backgroundColor = block.imageBackground ?: UIColor.clearColor;
  self.layer.cornerRadius = block.imageBorderRadius;
  self.layer.masksToBounds = block.imageBorderRadius > 0;

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
                         [strongSelf.host imageIntrinsicSize:image.size forUrl:url];
                       }];
}

- (void)layoutSubviews {
  [super layoutSubviews];
  _imageView.frame = self.bounds;
}

@end
