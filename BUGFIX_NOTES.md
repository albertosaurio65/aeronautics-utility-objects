# 万向节变速功能 - Bug修复说明

## 修复的问题

### 问题1：GUI打开方式与拆解冲突 ✅

**原问题**：
- Shift + 右键打开GUI
- Shift + 右键也用于拆解连接
- 两个功能冲突

**修复方案**：
- **普通右键**（空手）：打开GUI设置速度倍率
- **Shift + 右键**：拆解万向节连接

**修改文件**：
- `UniversalJointBlock.java` - `useWithoutItem()` 方法

---

### 问题2：速度倍率导致万向节被破坏 ✅

**原问题**：
- 0-1x 正转正常
- 其他倍率（>1x、负值、0x）触发万向节被自动破坏
- **即使没有连接任何应力消耗设备也会被破坏**

**根本原因**：
`refreshKinetics()`方法的执行顺序导致的时序问题：

```java
private void refreshKinetics() {
    this.detachKinetics();
    this.removeSource();      // ← 这里将speed设为0！
    this.attachKinetics();
}
```

当用户调整速度倍率时：
1. 万向节A调用`setSpeedRatio()` → 触发`refreshKinetics()`
2. `refreshKinetics()`调用`removeSource()`，将万向节A的`speed`设为0
3. 在`attachKinetics()`重新连接之前，万向节B的`validateKinetics()`被触发
4. 万向节B检查其source（万向节A），发现`sourceBE.speed == 0.0f`
5. Create的验证逻辑认为动力源已损坏，调用`removeSource()`和`detachKinetics()`
6. 万向节B被破坏

Create的`validateKinetics()`逻辑：
```java
private void validateKinetics() {
    if (this.hasSource()) {
        KineticBlockEntity sourceBE = ...;
        if (sourceBE == null || sourceBE.speed == 0.0f) {
            this.removeSource();
            this.detachKinetics();  // ← 导致方块被破坏
            return;
        }
    }
}
```

**修复方案**：
移除`refreshKinetics()`中的`removeSource()`调用，避免speed瞬间变成0：

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

这样可以保持speed不变，只刷新动力网络连接，避免触发Create的验证逻辑。

**修改文件**：
- `UniversalJointBlockEntity.java` - 修改`refreshKinetics()`方法

---

## 工作原理说明

### 速度传递机制

万向节通过 `propagateRotationTo()` 方法传递速度：

```java
@Override
public float propagateRotationTo(KineticBlockEntity target, ...) {
    if (target instanceof UniversalJointBlockEntity other && this.references(other)) {
        return this.speedRatio;  // 返回速度倍率
    }
    return super.propagateRotationTo(target, ...);
}
```

- 返回值是速度倍率
- Create会自动将这个倍率应用到目标方块
- 例如：输入100 RPM，倍率2.0x，输出200 RPM

### 动力网络刷新机制

当速度倍率改变时，需要刷新动力网络：
- `detachKinetics()` - 断开当前的动力连接
- `attachKinetics()` - 重新建立动力连接
- **不调用`removeSource()`** - 保持speed不变，避免触发验证逻辑

---

## 测试步骤

### 1. 测试GUI打开
1. 放置两个万向节并用连接杆连接
2. **空手普通右键**点击任意一个万向节
3. 应该打开GUI界面
4. **Shift + 右键**应该拆解连接

### 2. 测试速度倍率
1. 连接一个动力源（如手摇曲柄）到万向节A
2. 万向节A连接到万向节B
3. 万向节B连接到一个齿轮或其他动力设备
4. 打开万向节的GUI，测试不同倍率：

#### 测试用例

| 倍率 | 预期结果 | 状态 |
|------|---------|------|
| 1.0x | 输出=输入 | 正常 |
| 2.0x | 输出=输入×2 | 正常 |
| 0.5x | 输出=输入÷2 | 正常 |
| -1.0x | 反向，速度相同 | 正常 |
| -2.0x | 反向，速度×2 | 正常 |
| 0x | 停止传动 | 正常 |
| 4.0x | 输出=输入×4 | 正常 |
| -4.0x | 反向，速度×4 | 正常 |

### 3. 测试无应力设备情况
1. 只连接两个万向节，不连接任何应力消耗设备
2. 调整速度倍率到各种值（0x、2x、-1x、4x等）
3. **万向节不应该被破坏**（这是修复的关键）

### 4. 测试应力传递
1. 使用有应力限制的动力源（如水车）
2. 连接多个消耗应力的设备
3. 调整万向节倍率到高倍率（如4x）
4. 如果动力源容量不足，下游设备会过载停止工作
5. 万向节本身应该保持完好

---

## 注意事项

### 应力计算
下游设备会根据**实际速度**消耗应力：
- 输入：100 RPM，10 SU
- 倍率：2.0x
- 输出：200 RPM
- 下游设备消耗：根据200 RPM计算（可能是20 SU）

这是正常的！速度增加会导致应力需求增加。

### 动力源容量
如果动力源容量不足：
- 下游设备会过载并停止工作（显示红色应力条）
- 万向节本身不会被破坏
- 增加动力源容量或降低速度倍率可以解决过载问题

### 建议使用场景
- **加速传动**：低速高扭矩 → 高速低扭矩（需要更强的动力源）
- **减速传动**：高速低扭矩 → 低速高扭矩（降低应力需求）
- **反向传动**：改变旋转方向
- **精确控制**：通过GUI微调速度

---

## 已知限制

1. **应力不会被放大**：万向节只改变速度，不改变应力
2. **需要足够的动力源**：高倍率需要更强的动力源
3. **物理限制**：极高的速度可能导致游戏性能问题

---

## 更新日志

### v0.1.2 (2026-05-21)
- 🐛 修复：GUI打开方式改为普通右键，避免与拆解冲突
- 🐛 修复：移除refreshKinetics()中的removeSource()调用，防止速度倍率改变时万向节被破坏
- ✨ 改进：优化动力网络刷新逻辑，避免时序问题
- 📝 文档：添加详细的问题根源分析和测试步骤
