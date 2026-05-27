# 万向节应力问题修复 - 方案V2

## 问题根源分析

### 发现的关键问题
在 `setSpeedRatio()` 方法中调用了 `refreshKinetics()`，这会：
1. 调用 `detachKinetics()` - 从动力网络断开
2. 调用 `removeSource()` - 移除动力源
3. 调用 `attachKinetics()` - 重新连接到动力网络

这个过程会触发Create的应力系统**完全重新计算整个动力网络**，包括：
- 重新计算所有方块的速度
- 重新计算所有方块的应力
- 验证应力是否平衡

当速度倍率改变时，这个重新计算可能导致：
- 暂时的应力不平衡
- Create误判万向节为问题方块
- 触发自动破坏逻辑

## 修复方案V2

### 核心思路
**不要在速度倍率改变时立即刷新整个动力网络**，而是：
1. 只更新万向节自己的状态
2. 通知动力网络应力可能改变
3. 让Create自然地传播速度变化

### 代码修改

```java
public void setSpeedRatio(float ratio) {
    float clamped = Math.max(-4.0F, Math.min(4.0F, ratio));
    if (Math.abs(this.speedRatio - clamped) < 0.001F) {
        return;
    }

    this.speedRatio = clamped;
    this.setChanged();
    this.sendData();
    
    // 不要立即刷新动力网络
    // this.refreshKinetics();  // 注释掉
    
    // 而是通知网络应力可能改变
    if (this.hasNetwork()) {
        this.getOrCreateNetwork().updateStressFor(this, this.calculateStressApplied());
    }
}
```

### 同时保留的修复

```java
@Override
public float calculateStressApplied() {
    // 万向节作为纯传动方块，不消耗应力
    this.lastStressApplied = 0;
    return 0;
}

@Override
public float calculateAddedStressCapacity() {
    // 万向节不提供应力容量
    this.lastCapacityProvided = 0;
    return 0;
}
```

## 工作原理

### 速度传播机制
1. 玩家在GUI中调整速度倍率
2. `setSpeedRatio()` 更新 `speedRatio` 字段
3. **不触发网络刷新**
4. 下次动力网络自然更新时（如其他方块变化）
5. `propagateRotationTo()` 返回新的速度倍率
6. Create自然地传播新速度

### 应力处理
- 万向节明确声明不消耗应力（返回0）
- 万向节不提供应力容量（返回0）
- 只通知网络"我的应力是0"
- 不触发完整的网络重建

## 预期效果

### 优点
1. **避免网络重建**：不会触发完整的动力网络重新计算
2. **平滑过渡**：速度变化会在下次网络更新时自然应用
3. **应力隔离**：万向节明确不参与应力计算

### 可能的问题
1. **延迟更新**：速度变化可能不会立即生效
   - 解决：可以在下一个tick手动触发一次速度更新
2. **网络不同步**：如果两端在不同的动力网络
   - 解决：保持现有的连接验证逻辑

## 测试计划

### 测试1：基本功能
1. 连接两个万向节
2. 设置速度倍率为2.0x
3. 检查：万向节是否被破坏？
4. 检查：速度是否正确传递？

### 测试2：应力传递
1. 使用有限应力的动力源（如水车）
2. 连接万向节和多个消耗设备
3. 调整速度倍率
4. 检查：万向节是否被破坏？
5. 检查：下游设备是否正常工作？

### 测试3：动态调整
1. 在运行中的动力系统中
2. 实时调整速度倍率
3. 检查：系统是否稳定？
4. 检查：速度变化是否平滑？

### 测试4：极端情况
1. 速度倍率：4.0x（最大加速）
2. 速度倍率：-4.0x（最大反向）
3. 速度倍率：0x（停止）
4. 快速切换倍率
5. 检查：所有情况下万向节都不被破坏

## 备用方案

如果方案V2仍然无效，考虑：

### 备用方案1：延迟刷新
```java
// 在下一个tick刷新，而不是立即刷新
if (this.level != null && !this.level.isClientSide) {
    this.level.scheduleTick(this.worldPosition, this.getBlockState().getBlock(), 1);
}
```

### 备用方案2：使用Mixin
修改Create的应力验证逻辑，让万向节跳过检查：
```java
@Mixin(KineticNetwork.class)
public class KineticNetworkMixin {
    @Inject(method = "validateStress", at = @At("HEAD"), cancellable = true)
    private void skipUniversalJointValidation(KineticBlockEntity be, CallbackInfo ci) {
        if (be instanceof UniversalJointBlockEntity) {
            ci.cancel();
        }
    }
}
```

### 备用方案3：完全禁用应力
在万向节的Block类中添加标记，让Create完全忽略它的应力。

## 总结

方案V2的核心是：
- ✅ 不要在速度倍率改变时刷新整个动力网络
- ✅ 让Create自然地处理速度变化
- ✅ 明确声明万向节不参与应力计算
- ✅ 只通知网络"我的应力是0"

这应该能解决万向节被破坏的问题，同时保持速度传递功能正常工作。
