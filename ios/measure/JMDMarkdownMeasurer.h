#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Thread-safe measurement entry point used by the Fabric shadow node.
/// Shares the content cache with the mounted view, so measure work is
/// reused at mount time.
@interface JMDMarkdownMeasurer : NSObject

+ (CGFloat)measureMarkdown:(NSString *)markdown
                stylesJson:(NSString *)stylesJson
                imagesJson:(NSString *)imagesJson
                  maxWidth:(CGFloat)maxWidth
                 fontScale:(CGFloat)fontScale;

/// {"url":[w,h],...} -> url -> @[w, h]; nil for empty input.
+ (nullable NSDictionary<NSString *, NSArray<NSNumber *> *> *)parseImageSizes:(NSString *)json;

@end

NS_ASSUME_NONNULL_END
