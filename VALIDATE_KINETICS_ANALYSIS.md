# validateKinetics() 破坏条件完整分析

## validateKinetics() 源码

```java
private void validateKinetics() {
    // 情况1：如果这个方块有动力源（source）
    if (this.hasSource()) {
        KineticBlockEntity sourceBE;
        
        // 条件1.1：有source但没有network
        if (!this.hasNetwork()) {
            this.removeSource();  // 只移除source，不破坏方块
            return;
        }
        
        // 条件1.2：source所在区块未加载
        if (!this.level.isLoaded(this.source)) {
            return;  // 什么都不做，等待区块加载
        }
        
        // 获取source方块实体
        BlockEntity blockEntity = this.level.getBlockEntity(this.source);
        KineticBlockEntity kineticBlockEntity = sourceBE = blockEntity instanceof KineticBlockEntity ? (KineticBlockEntity)blockEntity : null;
        
        // 条件1.3：source不存在 或 source的速度为0
        if (sourceBE == null || sourceBE.speed == 0.0f) {
            this.removeSource();
            this.detachKinetics();  // ← 破坏方块！
            return;
        }
        return;
    }
    
    // 情况2：如果这个方块没有动力源
    // 条件2.1：方块有速度但不是动力源
    if (this.speed != 0.0f && this.getGeneratedSpeed() == 0.0f) {
        this.speed = 0.0f;  // 只清零速度，不破坏方块
    }
}
```

## 破坏条件总结

### 唯一会破坏方块的条件

**条件1.3：方块有source，但source不存在或source的速度为0**

```java
if (sourceBE == null || sourceBE.speed == 0.0f) {
    this.removeSource();
    this.detachKinetics();  // ← 这是唯一会破坏方块的地方
    return;
}
```

具体分解：
1. **`this.hasSource() == true`** - 方块有动力源
2. **`this.hasNetwork() == true`** - 方块在动力网络中
3. **`this.level.isLoaded(this.source) == true`** - source区块已加载
4. **`sourceBE == null`** - source位置没有方块实体，或者
5. **`sourceBE.speed == 0.0f`** - source的速度为0

### 不会破坏方块的情况

1. **条件1.1**：有source但没有network → 只调用`removeSource()`，不破坏
2. **条件1.2**：source区块未加载 → 什么都不做
3. **条件2.1**：没有source但有速度 → 只清零速度，不破坏

## 万向节被破坏的可能原因

基于上述分析，万向节被破坏只有一个原因：

**万向节B有source（万向节A），但万向节A的speed为0**

### 可能导致这种情况的场景

#### 场景1：refreshKinetics()时序问题
```
万向节A调用setSpeedRatio()
  → refreshKinetics()
    → detachKinetics()
    → removeSource()  // speed变成0
    → attachKinetics()
    
在removeSource()和attachKinetics()之间：
  万向节B的validateKinetics()被触发
    → 检查source（万向节A）
    → 发现sourceBE.speed == 0.0f
    → 破坏万向节B
```

#### 场景2：动力传播问题
```
万向节A的speed通过propagateRotationTo()传递给万向节B
如果propagateRotationTo()返回的倍率导致万向节B的speed计算为0
那么万向节B会被认为是"动力源失效"
```

#### 场景3：动力网络更新问题
```
当速度倍率改变时，Create的动力网络需要重新计算
如果在重新计算过程中，万向节A的speed暂时为0
万向节B的validateKinetics()可能检测到这个瞬间状态
```

#### 场景4：source引用错误
```
如果万向节B错误地将万向节A设为source
但万向节A实际上应该是万向节B的target
这种反向引用可能导致验证失败
```

## 需要检查的关键点

### 1. source的设置逻辑
万向节之间是否正确设置了source关系？
- 谁是source？
- 谁是target？
- 是否存在循环引用？

### 2. propagateRotationTo()的返回值
```java
@Override
public float propagateRotationTo(KineticBlockEntity target, ...) {
    if (target instanceof UniversalJointBlockEntity other && this.references(other)) {
        return this.speedRatio;  // 这个值是否可能导致问题？
    }
    return super.propagateRotationTo(target, ...);
}
```

问题：
- 如果speedRatio为0，会发生什么？
- 如果speedRatio为负数，会发生什么？
- Create如何处理这个返回值？

### 3. addPropagationLocations()的逻辑
```java
@Override
public List<BlockPos> addPropagationLocations(IRotate block, BlockState state, List<BlockPos> neighbours) {
    super.addPropagationLocations(block, state, neighbours);

    if (this.linkedPos == null || !this.isReferenceReady()) {
        return neighbours;
    }

    UniversalJointBlockEntity other = this.resolveLinkedJoint();
    if (other != null && other.references(this)) {
        neighbours.add(this.linkedPos);  // 添加另一个万向节为邻居
    }

    return neighbours;
}
```

问题：
- 两个万向节互相添加对方为邻居
- Create如何确定谁是source，谁是target？
- 是否可能导致循环依赖？

### 4. refreshKinetics()的调用时机
```java
public void setSpeedRatio(float ratio) {
    // ...
    this.speedRatio = clamped;
    this.setChanged();
    this.sendData();
    this.refreshKinetics();  // 这里调用
}
```

问题：
- 是否需要在设置speedRatio后立即刷新？
- 能否延迟刷新，避免时序问题？
- 能否只刷新一个万向节，而不影响另一个？

## 调试建议

### 1. 添加日志
在关键位置添加日志，追踪破坏过程：

```java
private void validateKinetics() {
    if (this.hasSource()) {
        // 添加日志
        System.out.println("[ValidateKinetics] " + this.worldPosition + " checking source: " + this.source);
        
        if (!this.hasNetwork()) {
            System.out.println("[ValidateKinetics] " + this.worldPosition + " no network, removing source");
            this.removeSource();
            return;
        }
        
        if (!this.level.isLoaded(this.source)) {
            System.out.println("[ValidateKinetics] " + this.worldPosition + " source not loaded");
            return;
        }
        
        BlockEntity blockEntity = this.level.getBlockEntity(this.source);
        KineticBlockEntity sourceBE = blockEntity instanceof KineticBlockEntity ? (KineticBlockEntity)blockEntity : null;
        
        if (sourceBE == null || sourceBE.speed == 0.0f) {
            System.out.println("[ValidateKinetics] " + this.worldPosition + " DESTROYING! sourceBE=" + sourceBE + ", speed=" + (sourceBE != null ? sourceBE.speed : "null"));
            this.removeSource();
            this.detachKinetics();
            return;
        }
    }
}
```

### 2. 检查source关系
在万向节连接时，打印source关系：

```java
public LinkResult createMutualLink(UniversalJointBlockEntity other) {
    // ...
    this.applyLinkReference(other.worldPosition, other.getContainingSubLevelId(), false);
    other.applyLinkReference(this.worldPosition, this.getContainingSubLevelId(), false);
    
    System.out.println("[Link] " + this.worldPosition + " linked to " + other.worldPosition);
    
    this.refreshKinetics();
    other.refreshKinetics();
    
    // 打印source关系
    System.out.println("[Link] " + this.worldPosition + " source: " + this.source);
    System.out.println("[Link] " + other.worldPosition + " source: " + other.source);
    
    return LinkResult.SUCCESS;
}
```

### 3. 监控speed变化
在setSpeed()中添加日志：

```java
public void setSpeed(float speed) {
    if (this.speed != speed) {
        System.out.println("[Speed] " + this.worldPosition + " speed changed: " + this.speed + " -> " + speed);
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = 2; i < Math.min(6, stackTrace.length); i++) {
            System.out.println("  at " + stackTrace[i]);
        }
    }
    this.speed = speed;
}
```

## 下一步行动

1. **添加调试日志**，确定破坏发生的确切时机和原因
2. **检查source关系**，确认两个万向节之间的source/target关系是否正确
3. **分析动力传播**，理解Create如何处理propagateRotationTo()的返回值
4. **测试不同场景**：
   - 只有两个万向节，无其他设备
   - 有动力源 + 两个万向节
   - 有动力源 + 两个万向节 + 下游设备
5. **对比其他变速方块**，看看Create自己的变速器或Simulated的模拟传动是如何实现的
