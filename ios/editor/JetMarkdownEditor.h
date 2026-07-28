#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// WYSIWYG markdown editor: hosts a UITextView, publishes its content
/// height into the shadow-node state (autogrow), and emits editing events.
@interface JetMarkdownEditor : RCTViewComponentView

@end

NS_ASSUME_NONNULL_END
