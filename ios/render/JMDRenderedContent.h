#import <UIKit/UIKit.h>

#import "JMDBlock.h"

NS_ASSUME_NONNULL_BEGIN

@interface JMDWidthLayout : NSObject
@property (nonatomic, strong) NSArray<JMDMeasuredBlock *> *measured;
@property (nonatomic, assign) CGFloat totalHeight;
@end

/// Parsed + rendered markdown block tree, shared between the Fabric measurer
/// (layout thread) and the mounted view (main thread). Per-width layout
/// results are cached.
@interface JMDRenderedContent : NSObject

@property (nonatomic, readonly) CGFloat gap;

- (instancetype)initWithBlocks:(NSArray<JMDBlock *> *)blocks
                           gap:(CGFloat)gap
                    topPadding:(CGFloat)topPadding
                 bottomPadding:(CGFloat)bottomPadding;

/// imageSizes: url -> @[@(width), @(height)] in points (dp).
+ (NSString *)keyForImageSizes:
    (nullable NSDictionary<NSString *, NSArray<NSNumber *> *> *)imageSizes;

- (JMDWidthLayout *)layoutForWidth:(CGFloat)width
                        imageSizes:(nullable NSDictionary<NSString *, NSArray<NSNumber *> *> *)imageSizes;

+ (CGFloat)stackHeight:(NSArray<JMDMeasuredBlock *> *)children gap:(CGFloat)gap;

@end

NS_ASSUME_NONNULL_END
