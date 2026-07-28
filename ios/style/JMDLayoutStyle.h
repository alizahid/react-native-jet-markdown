#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// One element's box style from stylesJson (point values).
@interface JMDLayoutStyle : NSObject

@property (nonatomic, readonly, nullable) UIColor *backgroundColor;
@property (nonatomic, readonly) CGFloat paddingLeft;
@property (nonatomic, readonly) CGFloat paddingRight;
@property (nonatomic, readonly) CGFloat paddingTop;
@property (nonatomic, readonly) CGFloat paddingBottom;
@property (nonatomic, readonly) CGFloat borderRadius;
@property (nonatomic, readonly) BOOL continuousCorners;
@property (nonatomic, readonly, nullable) UIColor *borderLeftColor;
@property (nonatomic, readonly) CGFloat borderLeftWidth;
@property (nonatomic, readonly, nullable) UIColor *borderRightColor;
@property (nonatomic, readonly) CGFloat borderRightWidth;
@property (nonatomic, readonly, nullable) UIColor *borderTopColor;
@property (nonatomic, readonly) CGFloat borderTopWidth;
@property (nonatomic, readonly, nullable) UIColor *borderBottomColor;
@property (nonatomic, readonly) CGFloat borderBottomWidth;

@property (nonatomic, readonly) CGFloat horizontalInset;
@property (nonatomic, readonly) CGFloat verticalInset;

+ (instancetype)fromJson:(nullable NSDictionary *)json defaults:(nullable JMDLayoutStyle *)defaults;

+ (instancetype)defaultsWithBackground:(nullable UIColor *)background
                               padding:(UIEdgeInsets)padding
                          borderRadius:(CGFloat)radius
                       borderLeftColor:(nullable UIColor *)borderLeftColor
                       borderLeftWidth:(CGFloat)borderLeftWidth;

@end

NS_ASSUME_NONNULL_END
