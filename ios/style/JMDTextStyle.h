#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// One element's text style as it arrived in stylesJson; nil fields inherit.
@interface JMDTextStyle : NSObject

@property (nonatomic, readonly, nullable) NSNumber *fontSize;
@property (nonatomic, readonly, nullable) NSNumber *fontWeight; // 100-900
@property (nonatomic, readonly, nullable) NSString *fontFamily;
@property (nonatomic, readonly, nullable) NSNumber *lineHeight;
@property (nonatomic, readonly, nullable) UIColor *color;
@property (nonatomic, readonly, nullable) NSArray<NSString *> *fontVariant;
@property (nonatomic, readonly, nullable) UIColor *textDecorationColor;
@property (nonatomic, readonly, nullable) NSString *textDecorationLine;
@property (nonatomic, readonly, nullable) NSString *textDecorationStyle;
@property (nonatomic, readonly, nullable) UIColor *backgroundColor;

+ (nullable instancetype)fromJson:(nullable NSDictionary *)json;

+ (nullable UIColor *)colorFromJson:(nullable id)value;

@end

NS_ASSUME_NONNULL_END
