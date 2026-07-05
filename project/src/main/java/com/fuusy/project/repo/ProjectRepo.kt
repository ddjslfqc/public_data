package com.fuusy.project.repo

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.fuusy.common.base.BaseRepository
import com.fuusy.common.network.BaseResp
import com.fuusy.common.network.DataState
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.net.StateLiveData
import com.fuusy.project.bean.ProjectContent
import com.fuusy.project.bean.ProjectItem
import com.fuusy.project.bean.ProjectTree
import kotlinx.coroutines.flow.Flow

/**
 * @date：2021/6/11
 * @author wangjian
 * @instruction："项目" Repository层
 */
class ProjectRepo(private val service: ProjectApi) : BaseRepository() {
    private val pageSize = 20


    /**
     * 获取项目列表
     */
    suspend fun getItemList(stateLiveData: StateLiveData<List<ProjectItem>>) {
        if (!ServerConfig.isMockData) {
            executeResp({ service.getItemList() }, stateLiveData)
        } else {
            // 如果API返回的数据为空，使用模拟数据
            if (stateLiveData.value?.data.isNullOrEmpty()) {
                val mockList = getMockProjectItems()
                // 修复类型不匹配：将List<ProjectItem>包装在BaseResp中
                val mockResponse = BaseResp(
                    errorCode = 200,
                    data = mockList,
                    errorMsg = "Success",
                )
                mockResponse.dataState = DataState.STATE_SUCCESS
                stateLiveData.postValue(mockResponse)
            }
        }
    }

    /**
     * 获取模拟项目数据
     */
    private fun getMockProjectItems(): List<ProjectItem> {
        return listOf(
            ProjectItem(
                item = "WSD0001",
                itemName = "锅炉管道清洗",
                unit = "深圳悦诚节能科技有限公司",
                address = "锅炉房1号机组",
                charge = "张三",
                phone = "13800138001",
                device = "WSD0001",
                content = "在线清洗机器人安装",
                connect = 1
            ),
            ProjectItem(
                item = "WSD0002",
                itemName = "脱硫设备维护",
                unit = "深圳悦诚节能科技有限公司",
                address = "脱硫塔区域",
                charge = "李四",
                phone = "13800138002",
                device = "WSD0002",
                content = "锅炉管道清洗",
                connect = 0
            ),
            ProjectItem(
                item = "WSD0003",
                itemName = "除尘器检修",
                unit = "郑州环保科技有限公司",
                address = "除尘器区域",
                charge = "王五",
                phone = "13800138003",
                device = "WSD0003",
                content = "脱硫设备日常维护",
                connect = 1
            ),
            ProjectItem(
                item = "WSD0004",
                itemName = "风机维护",
                unit = "郑州环保科技有限公司",
                address = "风机房",
                charge = "赵六",
                phone = "13800138004",
                device = "WSD0004",
                content = "脱硫设备日常维护",
                connect = 0
            ),
            ProjectItem(
                item = "WSD0005",
                itemName = "水泵检修",
                unit = "郑州环保科技有限公司",
                address = "水泵房",
                charge = "钱七",
                phone = "13800138005",
                device = "WSD0005",
                content = "脱硫设备日常维护",
                connect = 1
            ),
            ProjectItem(
                item = "WSD0006",
                itemName = "管道维护",
                unit = "郑州环保科技有限公司",
                address = "管道区域",
                charge = "孙八",
                phone = "13800138006",
                device = "WSD0006",
                content = "脱硫设备日常维护",
                connect = 0
            ),
            ProjectItem(
                item = "WSD0007",
                itemName = "阀门检修",
                unit = "郑州环保科技有限公司",
                address = "阀门区域",
                charge = "周九",
                phone = "13800138007",
                device = "WSD0007",
                content = "脱硫设备日常维护",
                connect = 1
            ),
            ProjectItem(
                item = "WSD0008",
                itemName = "仪表校准",
                unit = "郑州环保科技有限公司",
                address = "仪表区域",
                charge = "吴十",
                phone = "13800138008",
                device = "WSD0008",
                content = "脱硫设备日常维护",
                connect = 0
            )
        )
    }
}