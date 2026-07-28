#import "JMDTextStyle.h"

#import <React/RCTConvert.h>

@implementation JMDTextStyle

+ (nullable UIColor *)colorFromJson:(nullable id)value {
  if ([value isKindOfClass:[NSNumber class]]) {
    const uint32_t argb = [value unsignedIntValue];
    return [UIColor colorWithRed:((argb >> 16) & 0xFF) / 255.0
                           green:((argb >> 8) & 0xFF) / 255.0
                            blue:(argb & 0xFF) / 255.0
                           alpha:((argb >> 24) & 0xFF) / 255.0];
  }
  if ([value isKindOfClass:[NSDictionary class]]) {
    // Platform colors: {semantic: [...]} / {dynamic: {light, dark, ...}}.
    // RCTConvert resolves them to (possibly dynamic-provider) UIColors that
    // adapt to trait changes at draw time.
    return [RCTConvert UIColor:value];
  }
  return nil;
}

+ (nullable instancetype)fromJson:(nullable NSDictionary *)json {
  if (![json isKindOfClass:[NSDictionary class]]) {
    return nil;
  }
  JMDTextStyle *style = [JMDTextStyle new];
  if (style == nil) {
    return nil;
  }

  NSNumber *fontSize = [json[@"fontSize"] isKindOfClass:[NSNumber class]] ? json[@"fontSize"] : nil;
  NSString *fontWeightString =
      [json[@"fontWeight"] isKindOfClass:[NSString class]] ? json[@"fontWeight"] : nil;
  NSNumber *fontWeight = nil;
  if (fontWeightString != nil) {
    if ([fontWeightString isEqualToString:@"bold"]) {
      fontWeight = @700;
    } else if ([fontWeightString isEqualToString:@"normal"]) {
      fontWeight = @400;
    } else {
      const NSInteger parsed = fontWeightString.integerValue;
      if (parsed >= 100 && parsed <= 900) {
        fontWeight = @(parsed);
      }
    }
  }

  style->_fontSize = fontSize;
  style->_fontWeight = fontWeight;
  style->_fontFamily =
      [json[@"fontFamily"] isKindOfClass:[NSString class]] ? json[@"fontFamily"] : nil;
  style->_lineHeight =
      [json[@"lineHeight"] isKindOfClass:[NSNumber class]] ? json[@"lineHeight"] : nil;
  style->_color = [self colorFromJson:json[@"color"]];
  style->_fontVariant =
      [json[@"fontVariant"] isKindOfClass:[NSArray class]] ? json[@"fontVariant"] : nil;
  style->_textDecorationColor = [self colorFromJson:json[@"textDecorationColor"]];
  style->_textDecorationLine =
      [json[@"textDecorationLine"] isKindOfClass:[NSString class]] ? json[@"textDecorationLine"] : nil;
  style->_textDecorationStyle =
      [json[@"textDecorationStyle"] isKindOfClass:[NSString class]] ? json[@"textDecorationStyle"] : nil;
  style->_backgroundColor = [self colorFromJson:json[@"backgroundColor"]];
  return style;
}

@end
