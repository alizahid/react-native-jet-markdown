#import "JMDStyleConfig.h"

@implementation JMDMentionVariant

- (instancetype)initWithPattern:(NSRegularExpression *)pattern
                          style:(nullable JMDTextStyle *)style {
  if (self = [super init]) {
    _pattern = pattern;
    _style = style;
  }
  return self;
}

@end

@implementation JMDStyleConfig {
  NSDictionary *_root;
  NSMutableDictionary<NSString *, id> *_textStyles;
}

+ (instancetype)configWithJson:(NSString *)json {
  static NSCache<NSString *, JMDStyleConfig *> *cache;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    cache = [NSCache new];
    cache.countLimit = 16;
  });

  NSString *key = json.length > 0 ? json : @"{}";
  JMDStyleConfig *cached = [cache objectForKey:key];
  if (cached != nil) {
    return cached;
  }
  JMDStyleConfig *config = [[JMDStyleConfig alloc] initWithJson:key];
  [cache setObject:config forKey:key];
  return config;
}

- (instancetype)initWithJson:(NSString *)json {
  if (self = [super init]) {
    NSDictionary *root = nil;
    NSData *data = [json dataUsingEncoding:NSUTF8StringEncoding];
    if (data != nil) {
      id parsed = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
      if ([parsed isKindOfClass:[NSDictionary class]]) {
        root = parsed;
      }
    }
    _root = root ?: @{};
    _textStyles = [NSMutableDictionary new];

    NSDictionary *main = [_root[@"main"] isKindOfClass:[NSDictionary class]] ? _root[@"main"] : @{};
    // Container style-prop gap wins; the styles prop (defaultStyles.gap)
    // supplies the themed value; unstyled floor is 0.
    _gap = [self floatFrom:main key:@"gap"
                  fallback:[self floatFrom:_root key:@"gap" fallback:0]];
    _paddingLeft = [self floatFrom:main key:@"paddingLeft" fallback:0];
    _paddingRight = [self floatFrom:main key:@"paddingRight" fallback:0];
    _paddingTop = [self floatFrom:main key:@"paddingTop" fallback:0];
    _paddingBottom = [self floatFrom:main key:@"paddingBottom" fallback:0];
    _backgroundColor = [JMDTextStyle colorFromJson:main[@"backgroundColor"]];

    NSMutableArray<JMDMentionVariant *> *variants = [NSMutableArray new];
    NSDictionary *mention =
        [_root[@"mention"] isKindOfClass:[NSDictionary class]] ? _root[@"mention"] : nil;
    NSArray *variantPairs =
        [mention[@"variants"] isKindOfClass:[NSArray class]] ? mention[@"variants"] : @[];
    for (id pair in variantPairs) {
      if (![pair isKindOfClass:[NSArray class]] || [pair count] != 2) {
        continue;
      }
      NSString *patternString = [pair[0] isKindOfClass:[NSString class]] ? pair[0] : nil;
      if (patternString == nil) {
        continue;
      }
      NSRegularExpression *pattern =
          [NSRegularExpression regularExpressionWithPattern:patternString options:0 error:nil];
      if (pattern == nil) {
        continue;
      }
      [variants addObject:[[JMDMentionVariant alloc]
                              initWithPattern:pattern
                                        style:[JMDTextStyle fromJson:pair[1]]]];
    }
    _mentionVariants = variants;
  }
  return self;
}

- (CGFloat)floatFrom:(NSDictionary *)dict key:(NSString *)key fallback:(CGFloat)fallback {
  NSNumber *value = [dict[key] isKindOfClass:[NSNumber class]] ? dict[key] : nil;
  return value != nil ? value.doubleValue : fallback;
}

- (nullable NSDictionary *)rawSectionFor:(NSString *)key {
  return [_root[key] isKindOfClass:[NSDictionary class]] ? _root[key] : nil;
}

- (nullable JMDTextStyle *)textStyleFor:(NSString *)key {
  @synchronized(self) {
    id cached = _textStyles[key];
    if (cached != nil) {
      return cached == NSNull.null ? nil : cached;
    }
    JMDTextStyle *style = [JMDTextStyle fromJson:_root[key]];
    _textStyles[key] = style ?: (id)NSNull.null;
    return style;
  }
}

@end
