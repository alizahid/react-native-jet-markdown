#import <UIKit/UIKit.h>

#import "../render/JMDBlock.h"

#import "JMDMarkdownHost.h"

NS_ASSUME_NONNULL_BEGIN

/// Vertical stack of measured blocks; frames come from the measured tree.
@interface JMDBlockStackView : UIView

@property (nonatomic, weak, nullable) id<JMDMarkdownHost> host;

- (void)setBlocks:(NSArray<JMDMeasuredBlock *> *)blocks gap:(CGFloat)gap;

@end

NS_ASSUME_NONNULL_END
