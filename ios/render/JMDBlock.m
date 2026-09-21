#import "JMDBlock.h"

@implementation JMDRunBackground
@end

@implementation JMDListRow
@end

@implementation JMDTableRow
@end

@implementation JMDBlock
- (nullable NSString *)intrinsicSizeKey {
  return self.kind == JMDBlockKindVideo && self.videoUrl != nil
      ? [@"video:" stringByAppendingString:self.videoUrl] : self.imageUrl;
}
@end

@implementation JMDMeasuredBlock

- (instancetype)init {
  if (self = [super init]) {
    _children = @[];
    _markerHeights = @[];
    _rowContents = @[];
    _columnWidths = @[];
    _rowHeights = @[];
    _markerStorages = @[];
    _cellStorages = @[];
  }
  return self;
}

@end
