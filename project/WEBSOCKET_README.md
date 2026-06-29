# WebSocket 实时数据通信功能

## 概述

本项目已集成 WebSocket 功能，用于与服务器 `ws://111.229.81.186:9250/WebSocketAppGasServer/YXKJ0001` 进行实时数据通信。

## 功能特性

- ✅ 自动连接和重连机制
- ✅ 实时气体监测数据接收
- ✅ 告警信息处理
- ✅ 连接状态监控
- ✅ 错误处理和提示
- ✅ 心跳检测保持连接

## 文件结构

```
project/src/main/java/com/fuusy/project/
├── bean/
│   └── WebSocketMessage.kt          # WebSocket 消息数据模型
├── network/
│   └── WebSocketManager.kt          # WebSocket 连接管理器
├── repo/
│   └── WebSocketRepository.kt       # WebSocket 数据仓库
├── viewmodel/
│   └── ProjectDetailViewModel.kt    # 已集成 WebSocket 功能
└── ui/
    ├── ProjectDetailFragment.kt     # 已集成实时数据显示
    └── WebSocketUsageExample.kt     # 使用示例
```

## 使用方法

### 1. 基本使用

在你的 Activity 或 Fragment 中：

```kotlin
class YourActivity : AppCompatActivity() {
    private lateinit var viewModel: ProjectDetailViewModel
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化 ViewModel（会自动连接 WebSocket）
        viewModel = ProjectDetailViewModel()
        
        // 监听实时数据
        viewModel.monitoringData.observe(this) { data ->
            // 处理实时监测数据
            updateUI(data)
        }
        
        // 监听连接状态
        viewModel.connectionStatus.observe(this) { isConnected ->
            if (isConnected) {
                // 连接成功
                showConnectionSuccess()
            } else {
                // 连接断开
                showConnectionLost()
            }
        }
        
        // 监听告警信息
        viewModel.alerts.observe(this) { alerts ->
            // 处理告警信息
            handleAlerts(alerts)
        }
    }
}
```

### 2. 发送消息

```kotlin
// 发送文本消息
viewModel.sendWebSocketMessage("Hello Server")

// 发送 JSON 消息
val message = mapOf(
    "type" to "command",
    "action" to "getStatus",
    "timestamp" to System.currentTimeMillis()
)
viewModel.sendWebSocketJsonMessage(message)
```

### 3. 连接管理

```kotlin
// 检查连接状态
val isConnected = viewModel.isWebSocketConnected()

// 重新连接
viewModel.reconnectWebSocket()

// 断开连接
viewModel.disconnectWebSocket()

// 获取连接信息
val connectionInfo = viewModel.getWebSocketConnectionInfo()
```

## 数据格式

### 接收的数据格式

#### 气体监测数据
```json
{
    "deviceId": "YXKJ0001",
    "timestamp": 1640995200000,
    "oxygen": 20.9,
    "carbonMonoxide": 0.0,
    "hydrogenSulfide": 0.0,
    "methane": 0.0,
    "temperature": 25.0,
    "humidity": 60.0
}
```

#### 告警信息
```json
{
    "type": "gas_alert",
    "message": "一氧化碳浓度超标",
    "level": "high",
    "timestamp": 1640995200000
}
```

#### 状态信息
```json
{
    "status": "connected",
    "message": "连接成功"
}
```

### 发送的数据格式

```json
{
    "type": "test",
    "message": "Hello from Android client",
    "timestamp": 1640995200000
}
```

## 自动功能

### 1. 自动重连
- 连接断开时自动重连
- 最多重连 5 次
- 重连间隔 5 秒

### 2. 心跳检测
- 每 30 秒发送心跳包
- 保持连接活跃

### 3. 数据缓存
- 告警信息自动缓存最近 10 条
- 实时数据自动更新到 UI

## 错误处理

```kotlin
// 监听错误信息
viewModel.errorMessage.observe(this) { error ->
    if (error.isNotEmpty()) {
        // 显示错误信息
        showError(error)
        // 清除错误信息
        viewModel.clearWebSocketError()
    }
}
```

## 权限要求

确保在 `AndroidManifest.xml` 中添加了网络权限：

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

## 注意事项

1. **生命周期管理**：ViewModel 销毁时会自动断开 WebSocket 连接
2. **线程安全**：所有回调都在主线程执行，UI 更新安全
3. **内存管理**：告警信息自动限制数量，避免内存泄漏
4. **网络状态**：建议在网络状态变化时重新连接

## 测试

运行应用后，WebSocket 会自动连接到服务器。你可以：

1. 查看连接状态
2. 观察实时数据更新
3. 发送测试消息
4. 模拟网络断开测试重连功能

## 扩展

如需添加新的消息类型，只需：

1. 在 `WebSocketMessage.kt` 中添加新的数据类
2. 在 `WebSocketRepository.kt` 中添加处理逻辑
3. 在 ViewModel 中暴露相应的 LiveData

这样就完成了 WebSocket 实时数据通信功能的集成！ 