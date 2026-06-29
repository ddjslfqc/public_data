# Mobile 模块接口文档

---
## 接口服务器地址
http://47.110.156.186:9220
## 一、认证接口

### 1. 用户注册

**POST** `/mobile/auth/register`

**请求体**:
```json
{
    "username": "zhangsan",       // 账号（必填）
    "password": "123456",         // 密码（必填）
    "nickName": "张三",            // 姓名（必填）
    "deptId": 101                 // 部门ID（必填，从部门列表接口获取）
}
```

**响应**:
```json
{
    "code": 200,
    "msg": "操作成功",
    "data": true
}
```

---

### 2. 用户登录

**POST** `/mobile/auth/login`

**请求体**:
```json
{
    "username": "zhangsan",       // 账号（必填）
    "password": "123456"          // 密码（必填）
}
```

**响应**:
```json
{
    "code": 200,
    "msg": "操作成功",
    "data": {
        "userId": 1,              // 用户ID
        "username": "zhangsan",   // 账号
        "nickName": "张三",        // 姓名
        "deptId": 101,            // 部门ID
        "deptName": "用户管理"     // 部门名称
    }
}
```

---

### 3. 部门列表

**GET** `/mobile/auth/dept/list`

**请求参数**: 无

**响应**:
```json
{
    "code": 200,
    "msg": "操作成功",
    "data": [
        {
            "id": 100,            // 部门ID
            "name": "系统管理",    // 部门名称
            "parentId": 0,        // 父部门ID（0表示顶级）
            "children": [         // 子部门列表
                {
                    "id": 101,
                    "name": "用户管理",
                    "parentId": 100,
                    "children": []
                }
            ]
        }
    ]
}
```

---

## 二、OKR 我的目标接口

### 4. 获取我的目标页面数据

**GET** `/mobile/okr/my-goal`

**请求头**:
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| X-User-Id | Long | 是 | 当前用户ID |

**请求参数**:
| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| periodType | String | 否 | 周期类型：`quarter-1`~`quarter-4`（季度）、`year`（年度）、`month`（月度） |

**响应**:
```json
{
    "code": 200,
    "msg": "操作成功",
    "data": {
        "periods": [              // 周期选项列表
            {
                "label": "Q1 2026",
                "value": "quarter-1",
                "active": false
            },
            {
                "label": "Q2 2026",
                "value": "quarter-2",
                "active": true
            },
            {
                "label": "年度目标",
                "value": "year",
                "active": false
            }
        ],
        "currentObjective": {     // 当前选中的目标（最近创建的）
            "id": 1,
            "title": "完成Q2销售额目标",
            "periodType": "quarter",
            "periodLabel": "Q2 2026",
            "startDate": "2026-04-01",
            "endDate": "2026-06-30",
            "status": 0,
            "statusLabel": "进行中",
            "progress": 65,
            "approvalStatus": 1,
            "completedKrCount": 2,
            "totalKrCount": 4,
            "progressText": "2/4"
        },
        "objectives": [           // 目标列表（按周期筛选）
            {
                "id": 1,
                "title": "完成Q2销售额目标",
                "createTime": "2026-04-01 10:00:00",
                "periodType": "quarter",
                "periodLabel": "Q2 2026",
                "status": 0,
                "statusLabel": "进行中",
                "progress": 65,
                "approvalStatus": 1,
                "keyResults": [
                    {
                        "id": 1,
                        "title": "完成销售100万",
                        "targetValue": 1000000,
                        "weight": 40,
                        "currentValue": 700000,
                        "unit": "元",
                        "status": 0,
                        "approvalStatus": 1,
                        "achieved": false
                    }
                ]
            }
        ]
    }
}
```

**字段说明**:

| 字段 | 类型 | 说明 |
|------|------|------|
| periods | Array | 周期筛选选项 |
| periods[].label | String | 显示标签 |
| periods[].value | String | 值（用于筛选） |
| periods[].active | Boolean | 是否为当前选中 |
| currentObjective | Object | 当前目标 |
| currentObjective.id | Long | 目标ID |
| currentObjective.title | String | 目标标题 |
| currentObjective.periodType | String | 周期类型（month/quarter/year） |
| currentObjective.periodLabel | String | 周期标签（如"Q2 2026"） |
| currentObjective.startDate | String | 开始日期 |
| currentObjective.endDate | String | 结束日期 |
| currentObjective.status | Integer | 状态（0-进行中，1-已完成，2-已取消） |
| currentObjective.statusLabel | String | 状态显示文本 |
| currentObjective.progress | Integer | 完成进度（0-100） |
| currentObjective.approvalStatus | Integer | 审批状态（0-待审批，1-已通过，2-已拒绝，3-无需审批） |
| currentObjective.completedKrCount | Integer | 已完成KR数量 |
| currentObjective.totalKrCount | Integer | 总KR数量 |
| currentObjective.progressText | String | 进度文本（如"2/4"） |
| objectives | Array | 目标列表 |
| objectives[].keyResults | Array | 关键结果列表 |
| keyResults[].targetValue | Double | 目标值 |
| keyResults[].currentValue | Double | 当前值 |
| keyResults[].weight | Integer | 权重（百分比） |
| keyResults[].unit | String | 单位（如"元"、"%"） |
| keyResults[].achieved | Boolean | 是否已达成 |

---

## 三、通用响应格式

所有接口统一返回格式：

```json
{
    "code": 200,    // 状态码：200成功，非200失败
    "msg": "操作成功", // 提示信息
    "data": {}      // 业务数据
}
```

---

## 四、状态码说明

| 状态码 | 说明 |
|--------|------|
| 200 | 操作成功 |
| 500 | 服务器内部错误 |

---

## 五、审批状态说明

| 值 | 说明 |
|----|------|
| 0 | 待审批 |
| 1 | 已通过 |
| 2 | 已拒绝 |
| 3 | 无需审批 |

---

## 六、目标状态说明

| 值 | 说明 |
|----|------|
| 0 | 进行中 |
| 1 | 已完成 |
| 2 | 已取消 |