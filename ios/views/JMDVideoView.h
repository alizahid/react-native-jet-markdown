#import <UIKit/UIKit.h>
#import "../render/JMDBlock.h"
#import "JMDMarkdownHost.h"

NS_ASSUME_NONNULL_BEGIN

@interface JMDVideoView : UIView
@property (nonatomic, weak, nullable) id<JMDMarkdownHost> host;
- (void)bind:(JMDBlock *)block;
@end

NS_ASSUME_NONNULL_END
