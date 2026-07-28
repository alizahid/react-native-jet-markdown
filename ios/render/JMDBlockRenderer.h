#import <Foundation/Foundation.h>

#import "../style/JMDStyleConfig.h"
#import "JMDBlock.h"

NS_ASSUME_NONNULL_BEGIN

/// AST -> renderable block tree (attribute-stack inline rendering).
@interface JMDBlockRenderer : NSObject

+ (NSArray<JMDBlock *> *)renderMarkdown:(NSString *)markdown
                                 styles:(JMDStyleConfig *)styles
                              fontScale:(CGFloat)fontScale;

@end

NS_ASSUME_NONNULL_END
