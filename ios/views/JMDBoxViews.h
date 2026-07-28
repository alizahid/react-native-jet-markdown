#import <UIKit/UIKit.h>

#import "../render/JMDBlock.h"
#import "JMDMarkdownHost.h"

NS_ASSUME_NONNULL_BEGIN

/// Horizontal scroller for code blocks and tables. Cancels React's surface
/// touch handler when a drag begins so a wrapping Pressable's press dies,
/// exactly like React Native's own scroll views.
@interface JMDNestedScrollView : UIScrollView <UIScrollViewDelegate>
@end

/// Block quote: paints its box style, hosts a nested stack inside padding.
@interface JMDQuoteView : UIView
- (void)bind:(JMDMeasuredBlock *)measured
         gap:(CGFloat)gap
        host:(nullable id<JMDMarkdownHost>)host;
@end

/// Code block: paints its box, hosts unwrapped text in a horizontal scroller.
@interface JMDCodeBlockView : UIView
- (void)bind:(JMDMeasuredBlock *)measured;
@end

/// List: rows of a fixed-width marker column and nested content stacks.
@interface JMDListBlockView : UIView
- (void)bind:(JMDMeasuredBlock *)measured
         gap:(CGFloat)gap
        host:(nullable id<JMDMarkdownHost>)host;
@end

NS_ASSUME_NONNULL_END
