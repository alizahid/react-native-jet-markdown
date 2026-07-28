#import "JMDContentCache.h"

#import "../style/JMDStyleConfig.h"
#import "JMDBlockRenderer.h"

@implementation JMDContentCache

+ (JMDRenderedContent *)contentForMarkdown:(NSString *)markdown
                                stylesJson:(NSString *)stylesJson
                                 fontScale:(CGFloat)fontScale {
  static NSCache<NSString *, JMDRenderedContent *> *cache;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    cache = [NSCache new];
    cache.countLimit = 64;
  });

  // Full contents, not hashes: CFString's hash samples at most 96
  // characters, so distinct long documents collide and would render each
  // other's content. NSCache copies nothing — the key strings are shared.
  NSString *key = [NSString stringWithFormat:@"%@\x1f%@\x1f%.3f",
                                             markdown,
                                             stylesJson,
                                             fontScale];
  JMDRenderedContent *cached = [cache objectForKey:key];
  if (cached != nil) {
    return cached;
  }

  JMDStyleConfig *styles = [JMDStyleConfig configWithJson:stylesJson];
  NSArray<JMDBlock *> *blocks = [JMDBlockRenderer renderMarkdown:markdown
                                                          styles:styles
                                                       fontScale:fontScale];
  JMDRenderedContent *content = [[JMDRenderedContent alloc] initWithBlocks:blocks
                                                                       gap:styles.gap
                                                                topPadding:styles.paddingTop
                                                             bottomPadding:styles.paddingBottom];
  [cache setObject:content forKey:key];
  return content;
}

@end
