#import <UIKit/UIKit.h>

#import "../render/JMDBlock.h"
#import "JMDMarkdownHost.h"

NS_ASSUME_NONNULL_BEGIN

/// Table: paints the table box, hosts a cell grid in a horizontal scroller
/// so wide tables keep readable column widths.
@interface JMDTableView : UIView

- (void)bind:(JMDMeasuredBlock *)measured host:(nullable id<JMDMarkdownHost>)host;

@end

NS_ASSUME_NONNULL_END
