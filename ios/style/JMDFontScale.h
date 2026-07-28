#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// The Dynamic Type multiplier for the current content size category, using
/// React Native's own category table (RCTAccessibilityManager) so heights
/// measured with LayoutContext::fontSizeMultiplier match what the host
/// views render.
static inline CGFloat JMDFontSizeMultiplier(void) {
  static NSDictionary<UIContentSizeCategory, NSNumber *> *table;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    table = @{
      UIContentSizeCategoryExtraSmall : @0.823,
      UIContentSizeCategorySmall : @0.882,
      UIContentSizeCategoryMedium : @0.941,
      UIContentSizeCategoryLarge : @1.0,
      UIContentSizeCategoryExtraLarge : @1.118,
      UIContentSizeCategoryExtraExtraLarge : @1.235,
      UIContentSizeCategoryExtraExtraExtraLarge : @1.353,
      UIContentSizeCategoryAccessibilityMedium : @1.786,
      UIContentSizeCategoryAccessibilityLarge : @2.143,
      UIContentSizeCategoryAccessibilityExtraLarge : @2.643,
      UIContentSizeCategoryAccessibilityExtraExtraLarge : @3.143,
      UIContentSizeCategoryAccessibilityExtraExtraExtraLarge : @3.571,
    };
  });
  NSNumber *value =
      table[UIApplication.sharedApplication.preferredContentSizeCategory];
  return value != nil ? value.doubleValue : 1.0;
}

NS_ASSUME_NONNULL_END
