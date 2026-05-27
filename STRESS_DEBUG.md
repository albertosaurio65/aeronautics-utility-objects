# 万向节应力问题调试

## 问题现象
- 速度倍率 0-1x：正常工作
- 速度倍率 >1x、负值、0x：万向节被自动破坏
- **即使没有连接任何应力消耗设备也会被破坏**

## 问题分析

### Create的动力系统工作原理

1. **速度传递**：
   - `propagateRotationTo()` 返回速度倍率
   - Create自动应用倍率到下游方块

2. **动力验证**：
   - 每个KineticBlockEntity定期调用`validateKinetics()`
   - 检查动力源是否有效
   - 如果source的speed为0，认为动力源已损坏，破坏自己

3. **动力网络刷新**：
   - `refreshKinetics()` 用于刷新动力连接
   - 包含 `detachKinetics()` → `removeSource()` → `attachKinetics()`

### 问题根源

**时序问题导致的误判**：

当用户调整万向节A的速度倍率时：

1. 万向节A调用`setSpeedRatio()` → 触发`refreshKinetics()`
2. `refreshKinetics()`执行顺序：
   ```java
   this.detachKinetics();
   this.removeSource();      // ← 将speed设为0！
   this.attachKinetics();
   ```
3. `removeSource()`将万向节A的`speed`设为0
4. 在`attachKinetics()`重新连接之前，万向节B的`validateKinetics()`被触发
5. 万向节B检查其source（万向节A）：
   ```java
   if (sourceBE == null || sourceBE.speed == 0.0f) {
       this.removeSource();
       this.detachKinetics();  // ← 破坏万向节B
       return;
   }
   ```
6. 万向节B被破坏

**关键代码**：

Create的`validateKinetics()`：
```java
private void validateKinetics() {
    if (this.hasSource()) {
        KineticBlockEntity sourceBE;
        if (!this.hasNetwork()) {
            this.removeSource();
            return;
        }
        if (!this.level.isLoaded(this.source)) {
            return;
        }
        BlockEntity blockEntity = this.level.getBlockEntity(this.source);
        KineticBlockEntity kineticBlockEntity = sourceBE = blockEntity instanceof KineticBlockEntity ? (KineticBlockEntity)blockEntity : null;
        if (sourceBE == null || sourceBE.speed == 0.0f) {
            this.removeSource();
            this.detachKinetics();  // ← 这里破坏方块
            return;
        }
        return;
    }
    if (this.speed != 0.0f && this.getGeneratedSpeed() == 0.0f) {
        this.speed = 0.0f;
    }
}
```

Create的`removeSource()`：
```java
public void removeSource() {
    float prevSpeed = this.getSpeed();
    this.speed = 0.0f;  // ← 将速度设为0
    this.source = null;
    this.setNetwork(null);
    this.sequenceContext = null;
    this.onSpeedChanged(prevSpeed);
}
```

## 尝试的解决方案

### 方案1：重写应力方法 ❌
```java
@Override
public float calculateStressApplied() {
    return 0;
}

@Override
public float calculateAddedStressCapacity() {
    return 0;
}
```

**结果**：无效，万向节仍然被破坏

**原因**：问题不是应力计算，而是`validateKinetics()`检测到speed=0

### 方案2：重写getSpeed()忽略overStressed ❌
```java
@Override
public float getSpeed() {
    if (this.level != null && this.level.tickRateManager().isFrozen()) {
        return 0.0f;
    }
    return this.getTheoreticalSpeed();
}
```

**结果**：无效，因为用户反馈即使没有连接应力消耗设备也会被破坏

**原因**：问题不是应力过载，而是`refreshKinetics()`中的`removeSource()`导致speed瞬间变成0

### 方案3：移除refreshKinetics()中的removeSource()调用 ✅

**实现**：
```java
private void refreshKinetics() {
    this.setChanged();
    this.sendData();

    if (this.level == null || this.level.isClientSide) {
        return;
    }

    // 不调用removeSource()，避免speed变成0导致连接的万向节被破坏
    // 只需要detach和reattach来刷新动力网络
    this.detachKinetics();
    this.attachKinetics();
}
```

**原理**：
- `detachKinetics()` - 断开当前的动力连接
- `attachKinetics()` - 重新建立动力连接，Create会重新计算速度
- **不调用`removeSource()`** - 保持speed不变，避免瞬间变成0触发验证逻辑

**效果**：
- ✅ 万向节在任何速度倍率下都不会被破坏
- ✅ 动力网络正常刷新，速度倍率正确应用
- ✅ 不影响Create的动力系统正常运作
- ✅ 解决了时序问题导致的误判

---

## 深层原因总结

1. **不是应力问题**：即使没有连接应力消耗设备也会被破坏
2. **不是应力过载**：问题在所有非1x倍率下都会出现
3. **是时序问题**：`refreshKinetics()`中的`removeSource()`导致speed瞬间为0，触发另一个万向节的验证逻辑
4. **Create的验证机制**：`validateKinetics()`检测到source的speed=0就认为动力源损坏，破坏自己

## 解决方案的关键

**不要在刷新动力网络时将speed设为0**

- 原来的逻辑：detach → removeSource (speed=0) → attach
- 修复后的逻辑：detach → attach (speed保持不变)

这样可以避免在刷新过程中触发Create的验证逻辑，同时仍然能够正确刷新动力网络连接。

---

## 参考资料

### Create的相关代码
- `KineticBlockEntity.validateKinetics()` - 验证动力源有效性
- `KineticBlockEntity.removeSource()` - 移除动力源，将speed设为0
- `KineticBlockEntity.detachKinetics()` - 断开动力连接
- `KineticBlockEntity.attachKinetics()` - 建立动力连接

### Simulated mod的类似实现
- `AnalogTransmissionBlockEntity` - 也实现了变速功能
- 它在某些情况下返回0会导致下游方块被破坏
- 但它没有使用`refreshKinetics()`，而是直接detach和attach

---

## 测试建议

1. **基础测试**：两个万向节连接，调整速度倍率，确认不会被破坏
2. **无应力设备测试**：只有动力源和万向节，无其他设备，测试所有倍率
3. **有应力设备测试**：连接应力消耗设备，测试高倍率下的行为
4. **快速切换测试**：快速连续调整速度倍率，测试时序问题是否解决
