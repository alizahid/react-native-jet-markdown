#import <UIKit/UIKit.h>

#import "../render/JMDBlock.h"
#import "JMDMarkdownHost.h"

NS_ASSUME_NONNULL_BEGIN

/// One markdown image: rounded-corner aspect-fit bitmap, background while
/// loading, and an optional caption below it. Requests are URL-owned; this
/// view only listens.
@interface JMDImageView : UIView

@property (nonatomic, weak, nullable) id<JMDMarkdownHost> host;
@property (nonatomic, readonly, nullable) NSString *imageUrl;
/// The image area, excluding the caption (lightbox frames).
@property (nonatomic, readonly) UIView *mediaView;

- (void)bind:(JMDMeasuredBlock *)measured;

@end

NS_ASSUME_NONNULL_END
