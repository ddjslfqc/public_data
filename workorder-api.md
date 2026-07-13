# 工单模块 API 文档


**Base URL**: `https://ios.yceil.com/`

## 一、业务逻辑概述

### 1.1 状态流转

```
待提交(DRAFT) → [提交] → 待认领(PENDING) → [认领/审批通过] → 处理中(PROCESSING) 
→ [提交处理结果] → 待评价(PENDING_EVALUATION) → [评价通过] → 已完成(COMPLETED)
                              ↑
                      [驳回] → 驳回(REJECTED)
```

### 1.2 核心业务流程

| 角色 | 操作 | 说明 |
|------|------|------|
| 提报人 | 创建工单 | 填写工单名称、类别、处理部门、优先级、需求说明等 |
| 提报人 | 暂存草稿 | 保存为待提交状态，可继续编辑 |
| 提报人 | 提交工单 | 工单进入待认领状态 |
| 部门人员 | 认领工单 | 将状态改为处理中 |
| 处理人 | 处理工单 | 更新处理进度，提交处理结果 |
| 审批人 | 审批工单 | 根据当前状态流转到下一阶段 |
| 提报人 | 评价工单 | 待评价状态下进行评价 |

---

## 二、接口列表

| API路径 | 方法 | 功能 |
|---------|------|------|
| `/mobile/workorder/list` | GET | 获取工单列表（需认证） |
| `/mobile/workorder/all` | GET | 获取所有工单列表（无需认证） |
| `/mobile/workorder/detail/{id}` | GET | 获取工单详情 |
| `/mobile/workorder/create` | POST | 创建工单 |
| `/mobile/workorder/attachment/upload` | POST | 上传工单附件 |
| `/mobile/workorder/attachment/{id}` | DELETE | 删除工单附件 |
| `/mobile/workorder/approve` | POST | 审批工单 |
| `/mobile/workorder/options` | GET | 获取选项列表（类别、部门、优先级等） |

---

## 三、接口详细说明

### 3.1 获取工单列表

**请求**
```
GET /mobile/workorder/list?status={status}&scope={scope}
Headers: X-User-Id: {userId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID（从Header获取） |
| status | String | 否 | 状态筛选（见下方状态值说明），不传则返回全部 |
| scope | String | 否 | 列表范围，默认 `related` |

**scope 说明**

| 值 | 含义 |
|----|------|
| `related`（默认） | 我相关的：指派我处理的 + 我提报的（已指派他人处理中的除外） |
| `handled_completed` | 我作为处理人完成的工单 |
| `all` | 组织内全部工单 |

**状态值说明**

| 值 | 含义 | 对应状态 |
|----|------|----------|
| （不传） | 全部 | 返回所有状态的工单 |
| `draft` | 待提交 | 草稿状态，未提交的工单 |
| `pending` | 待认领 | 已提交，等待处理人认领 |
| `processing` | 处理中 | 已认领，正在处理 |
| `pending_evaluation` | 待评价 | 处理完成，等待提报人评价 |
| `completed` | 已完成 | 评价通过，工单结束 |
| `rejected` | 驳回 | 审批被驳回 |

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": "1",
      "no": "WD202606180001",
      "title": "客户门户体验优化需求",
      "brief": "客户反馈现有门户操作路径过长...",
      "typeCode": "PERSONNEL",
      "typeName": "人员因素",
      "priority": "P1",
      "project": "A项目",
      "expectedCompletionTime": "2026-06-18 18:00:00",
      "status": "PENDING",
      "statusLabel": "待认领",
      "recordCreator": "1",
      "recordCreatorName": "张三",
      "recordTime": "2026-06-18 09:30:00",
      "responsibleDept": "5",
      "responsibleDeptName": "软件研发部",
      "rectificationPerson": "10"
    }
  ]
}
```

### 3.2 获取所有工单列表

**请求**
```
GET /mobile/workorder/all?status={status}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| status | String | 否 | 状态筛选（见上方状态值说明），不传则返回全部 |

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": "1",
      "no": "WD202606180001",
      "title": "客户门户体验优化需求",
      "brief": "客户反馈现有门户操作路径过长...",
      "typeCode": "PERSONNEL",
      "typeName": "人员因素",
      "priority": "P1",
      "project": "A项目",
      "expectedCompletionTime": "2026-06-18 18:00:00",
      "status": "PENDING",
      "statusLabel": "待认领",
      "recordCreator": "1",
      "recordCreatorName": "张三",
      "recordTime": "2026-06-18 09:30:00",
      "responsibleDept": "5",
      "responsibleDeptName": "软件研发部",
      "rectificationPerson": "10"
    }
  ]
}
```

### 3.3 获取工单详情

**请求**
```
GET /mobile/workorder/detail/{id}
Headers: X-User-Id: {userId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID（从Header获取） |
| id | String | 是 | 工单ID（路径参数） |

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "1",
    "no": "WD202606180001",
    "title": "客户门户体验优化需求",
    "brief": "客户反馈现有门户操作路径过长...",
    "typeCode": "PERSONNEL",
    "typeName": "人员因素",
    "priority": "P1",
    "project": "A项目",
    "expectedCompletionTime": "2026-06-18 18:00:00",
    "status": "PROCESSING",
    "statusLabel": "处理中",
    "recordCreator": "1",
    "recordCreatorName": "张三",
    "recordTime": "2026-06-18 09:30:00",
    "responsibleDept": "5",
    "responsibleDeptName": "软件研发部",
    "rectificationPerson": "10",
    "rectificationPersonName": "王健",
    "reason": "",
    "consequences": "",
    "controlLevel": "",
    "rectificationScheme": "",
    "device": "",
    "attachments": [
      {
        "id": "1",
        "fileName": "需求文档.pdf",
        "filePath": "http://10.237.25.119:9300/profile/ai/xxx.pdf",
        "fileType": "pdf"
      }
    ],
    "approvalRecords": []
  }
}
```

### 3.4 创建工单

**请求**
```
POST /mobile/workorder/create
Headers: X-User-Id: {userId}
Content-Type: application/json
```

```json
{
  "title": "客户门户体验优化需求",
  "brief": "客户反馈现有门户操作路径过长，需简化登录后首页到核心功能的跳转流程。",
  "typeCode": "PERSONNEL",
  "responsibleDept": "5",
  "rectificationPerson": "10",
  "priority": "P1",
  "project": "A项目",
  "expectedCompletionTime": "2026-06-18T18:00:00",
  "reason": "",
  "consequences": "",
  "controlLevel": "",
  "rectificationScheme": "",
  "device": ""
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| title | String | 是 | 工单名称 |
| brief | String | 是 | 需求说明 |
| typeCode | String | 是 | 类别编码（从options接口获取） |
| responsibleDept | String | 是 | 处理人部门编码 |
| rectificationPerson | String | 否 | 处理人ID（不指定则公开抢单） |
| priority | String | 否 | 优先级（P1/P2/P3） |
| project | String | 否 | 隶属项目名称 |
| expectedCompletionTime | DateTime | 否 | 期望完成时间 |

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "1",
    "no": "WD202606180001"
  }
}
```

### 3.5 上传工单附件

**请求**
```
POST /mobile/workorder/attachment/upload?workOrderId={workOrderId}
Headers: X-User-Id: {userId}
Content-Type: multipart/form-data
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID（从Header获取） |
| workOrderId | String | 是 | 工单ID |
| file | File | 是 | 附件文件（支持图片/视频/PDF等） |

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "1",
    "fileName": "需求文档.pdf",
    "filePath": "http://10.237.25.119:9300/profile/ai/xxx.pdf",
    "fileType": "pdf"
  }
}
```

### 3.6 删除工单附件

**请求**
```
DELETE /mobile/workorder/attachment/{id}
Headers: X-User-Id: {userId}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID（从Header获取） |
| id | String | 是 | 附件ID（路径参数） |

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

### 3.7 审批工单

**请求**
```
POST /mobile/workorder/approve
Headers: X-User-Id: {userId}
Content-Type: application/json
```

```json
{
  "workOrderId": "1",
  "approvalResult": "APPROVE",
  "approvalOpinion": "同意，尽快处理"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| userId | Long | 是 | 用户ID（从Header获取） |
| workOrderId | String | 是 | 工单ID |
| approvalResult | String | 是 | 审批结果：`APPROVE`（通过）/ `REJECT`（驳回） |
| approvalOpinion | String | 否 | 审批意见 |

**状态流转规则**

| 当前状态 | 审批结果 | 下一状态 |
|----------|----------|----------|
| PENDING（待认领） | APPROVE | PROCESSING（处理中） |
| PROCESSING（处理中） | APPROVE | PENDING_EVALUATION（待评价） |
| PENDING_EVALUATION（待评价） | APPROVE | COMPLETED（已完成） |
| 任意状态 | REJECT | REJECTED（驳回） |

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": null
}
```

### 3.8 获取选项列表

**请求**
```
GET /mobile/workorder/options
```

**响应**
```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "hazardLevels": [
      {"value": "GENERAL", "label": "一般隐患"},
      {"value": "MAJOR", "label": "较大隐患"},
      {"value": "CRITICAL", "label": "重大隐患"}
    ],
    "hazardTypes": [
      {"value": "EQUIPMENT", "label": "设备设施"},
      {"value": "ENVIRONMENT", "label": "环境因素"},
      {"value": "MANAGEMENT", "label": "管理因素"},
      {"value": "PERSONNEL", "label": "人员因素"}
    ],
    "priorities": [
      {"value": "P1", "label": "P1"},
      {"value": "P2", "label": "P2"},
      {"value": "P3", "label": "P3"}
    ],
    "departments": [
      {"value": "1", "label": "检修部"},
      {"value": "2", "label": "运行部"},
      {"value": "3", "label": "安全环保部"},
      {"value": "4", "label": "设备管理部"},
      {"value": "5", "label": "软件研发部"}
    ]
  }
}
```

---

## 四、数据字典

### 4.1 工单状态

| 状态码 | 显示名称 | 说明 |
|--------|----------|------|
| DRAFT | 待提交 | 草稿状态，未提交 |
| PENDING | 待认领 | 已提交，等待处理人认领 |
| PROCESSING | 处理中 | 已认领，正在处理 |
| PENDING_EVALUATION | 待评价 | 处理完成，等待提报人评价 |
| COMPLETED | 已完成 | 评价通过，工单结束 |
| REJECTED | 驳回 | 审批驳回 |

### 4.2 优先级

| 值 | 说明 |
|----|------|
| P1 | 高优先级 |
| P2 | 中优先级 |
| P3 | 低优先级 |

### 4.3 类别

| 值 | 说明 |
|----|------|
| EQUIPMENT | 设备设施 |
| ENVIRONMENT | 环境因素 |
| MANAGEMENT | 管理因素 |
| PERSONNEL | 人员因素 |

---

## 五、数据库表结构

### 5.1 work（工单主表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR | 工单ID |
| no | VARCHAR | 工单编号 |
| hidden_danger_name | VARCHAR | 工单名称 |
| hidden_danger_description | VARCHAR | 需求说明 |
| hidden_danger_category | VARCHAR | 类别 |
| responsible_department | VARCHAR | 处理部门 |
| rectification_person | VARCHAR | 处理人 |
| wf_node_code | VARCHAR | 当前状态码 |
| status | VARCHAR | 状态（ACTIVE/DELETED） |
| create_by | VARCHAR | 创建人 |
| create_time | DATETIME | 创建时间 |
| update_time | DATETIME | 更新时间 |
| priority | VARCHAR | 优先级 |
| project | VARCHAR | 隶属项目 |
| expected_completion_time | DATETIME | 期望完成时间 |

### 5.2 xjgd_hd_wf_node（审批记录表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR | 记录ID |
| hd_no | VARCHAR | 工单编号 |
| wf_node_code | VARCHAR | 状态码 |
| responsible_person | VARCHAR | 责任人 |
| approval_opinion | VARCHAR | 审批意见 |
| approval_result | VARCHAR | 审批结果 |
| created_by | VARCHAR | 创建人 |
| created_time | DATETIME | 创建时间 |

### 5.3 xjgd_hd_attachment（附件表）

| 字段 | 类型 | 说明 |
|------|------|------|
| id | VARCHAR | 附件ID |
| hd_no | VARCHAR | 工单编号 |
| file_name | VARCHAR | 文件名 |
| file_path | VARCHAR | 文件访问URL |
| file_type | VARCHAR | 文件类型 |
| created_by | VARCHAR | 创建人 |
| created_time | DATETIME | 创建时间 |