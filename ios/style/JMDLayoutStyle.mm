#import "JMDLayoutStyle.h"

#import "JMDTextStyle.h"

@implementation JMDLayoutStyle

+ (instancetype)defaultsWithBackground:(nullable UIColor *)background
                               padding:(UIEdgeInsets)padding
                          borderRadius:(CGFloat)radius
                       borderLeftColor:(nullable UIColor *)borderLeftColor
                       borderLeftWidth:(CGFloat)borderLeftWidth {
  JMDLayoutStyle *style = [JMDLayoutStyle new];
  style->_backgroundColor = background;
  style->_paddingLeft = padding.left;
  style->_paddingRight = padding.right;
  style->_paddingTop = padding.top;
  style->_paddingBottom = padding.bottom;
  style->_borderRadius = radius;
  style->_borderLeftColor = borderLeftColor;
  style->_borderLeftWidth = borderLeftWidth;
  return style;
}

+ (instancetype)fromJson:(nullable NSDictionary *)json defaults:(nullable JMDLayoutStyle *)defaults {
  JMDLayoutStyle *base = defaults ?: [JMDLayoutStyle new];
  if (![json isKindOfClass:[NSDictionary class]]) {
    return base;
  }

  JMDLayoutStyle *style = [JMDLayoutStyle new];
  auto number = [json](NSString *key, CGFloat fallback) -> CGFloat {
    NSNumber *value = [json[key] isKindOfClass:[NSNumber class]] ? json[key] : nil;
    return value != nil ? value.doubleValue : fallback;
  };
  auto color = [json](NSString *key, UIColor *fallback) -> UIColor * {
    UIColor *value = [JMDTextStyle colorFromJson:json[key]];
    return value ?: fallback;
  };

  style->_backgroundColor = color(@"backgroundColor", base.backgroundColor);
  style->_paddingLeft = number(@"paddingLeft", base.paddingLeft);
  style->_paddingRight = number(@"paddingRight", base.paddingRight);
  style->_paddingTop = number(@"paddingTop", base.paddingTop);
  style->_paddingBottom = number(@"paddingBottom", base.paddingBottom);
  style->_borderRadius = number(@"borderRadius", base.borderRadius);
  style->_continuousCorners =
      [json[@"borderCurve"] isKindOfClass:[NSString class]] &&
      [json[@"borderCurve"] isEqualToString:@"continuous"];
  style->_borderLeftColor = color(@"borderLeftColor", base.borderLeftColor);
  style->_borderLeftWidth = number(@"borderLeftWidth", base.borderLeftWidth);
  style->_borderRightColor = color(@"borderRightColor", base.borderRightColor);
  style->_borderRightWidth = number(@"borderRightWidth", base.borderRightWidth);
  style->_borderTopColor = color(@"borderTopColor", base.borderTopColor);
  style->_borderTopWidth = number(@"borderTopWidth", base.borderTopWidth);
  style->_borderBottomColor = color(@"borderBottomColor", base.borderBottomColor);
  style->_borderBottomWidth = number(@"borderBottomWidth", base.borderBottomWidth);
  return style;
}

- (CGFloat)horizontalInset {
  return _paddingLeft + _paddingRight + _borderLeftWidth + _borderRightWidth;
}

- (CGFloat)verticalInset {
  return _paddingTop + _paddingBottom + _borderTopWidth + _borderBottomWidth;
}

@end
