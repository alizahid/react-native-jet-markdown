#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Callbacks from block views up to the host component view.
@protocol JMDMarkdownHost <NSObject>
- (void)imageIntrinsicSize:(CGSize)size forUrl:(NSString *)url;
- (BOOL)isSpoilerRevealed:(NSInteger)spoilerId;
- (void)toggleSpoiler:(NSInteger)spoilerId;
- (void)linkPressed:(NSString *)url;
- (void)linkLongPressed:(NSString *)url;
/// `frame` is the pressed image's frame in screen coordinates (points).
- (void)imagePressed:(NSString *)url frame:(CGRect)frame;
@end

NS_ASSUME_NONNULL_END
