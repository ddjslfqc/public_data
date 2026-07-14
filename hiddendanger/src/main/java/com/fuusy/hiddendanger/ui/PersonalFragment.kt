package com.fuusy.hiddendanger.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.fuusy.common.base.BaseVmFragment
import com.fuusy.hiddendanger.BR
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.FragmentPersonalBinding
import com.fuusy.hiddendanger.util.AppDialogHelper
import com.fuusy.hiddendanger.viewmodel.PersonalViewModel

@Route(path = "/hiddendanger/PersonalFragment")
class PersonalFragment : BaseVmFragment<FragmentPersonalBinding, PersonalViewModel>() {

    override fun initContentView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): Int = R.layout.fragment_personal

    override fun initVariableId(): Int = BR.viewModel

    override fun initViewModel(): PersonalViewModel {
        return PersonalViewModel(requireActivity().application)
    }

    override fun initViewObservable() {
        applyStatusBarPadding()
        mViewModel.userInfo.observe(viewLifecycleOwner, Observer { user ->
            mDataBinding.tvName.text = user.name
            mDataBinding.tvDepartment.text = user.subtitle()
            Glide.with(this)
                .load(user.avatarUrl)
                .apply(RequestOptions().transform(CircleCrop()))
                .placeholder(R.mipmap.default_head_photo)
                .error(R.mipmap.default_head_photo)
                .into(mDataBinding.ivAvatar)
        })

        mViewModel.completedTaskCount.observe(viewLifecycleOwner, Observer {
            mDataBinding.tvCompletedCount.text = it.toString()
        })

        mViewModel.averageRating.observe(viewLifecycleOwner, Observer {
            mDataBinding.tvAverageRating.text = it
        })

        mViewModel.okrPendingApprovalCount.observe(viewLifecycleOwner, Observer { count ->
            val subtitle = if (count > 0) "$count 条待处理" else "KR 与进度审批"
            mDataBinding.menuOkrApproval.tvSubtitle.visibility = android.view.View.VISIBLE
            mDataBinding.menuOkrApproval.tvSubtitle.text = subtitle
        })

        mViewModel.isDeptLeader.observe(viewLifecycleOwner, Observer { isLeader ->
            val visible = if (isLeader) android.view.View.VISIBLE else android.view.View.GONE
            mDataBinding.menuOkrApproval.rootMenuItem.visibility = visible
            mDataBinding.dividerOkrApproval.visibility = visible
        })

        mViewModel.logout.observe(viewLifecycleOwner, Observer {
            if (it) {
                showToast("已退出登录")
                ARouter.getInstance()
                    .build("/login/LoginActivity")
                    .withFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .navigation()
                activity?.finish()
            }
        })

        setupMenuItems()
        setupStatCards()

        mDataBinding.btnLogout.setOnClickListener {
            AppDialogHelper.showConfirm(
                context = requireContext(),
                title = "退出登录",
                message = "确定要退出当前账号吗？",
                confirmText = "退出"
            ) {
                mViewModel.logout()
            }
        }

        mViewModel.loadData()
    }

    private fun setupMenuItems() {
        bindMenu(
            binding = mDataBinding.menuGoal,
            icon = R.drawable.ic_personal_goal,
            title = "我的目标",
            subtitle = "个人 OKR"
        ) {
            ARouter.getInstance()
                .build("/hiddendanger/MyGoalsActivity")
                .navigation()
        }

        bindMenu(
            binding = mDataBinding.menuOkrApproval,
            icon = R.drawable.ic_personal_evaluation,
            title = "待我审批",
            subtitle = "KR 与进度审批"
        ) {
            ARouter.getInstance()
                .build("/hiddendanger/KrApprovalActivity")
                .navigation()
        }

        bindMenu(
            binding = mDataBinding.menuOrgTeam,
            icon = R.drawable.ic_personal_team,
            title = "团队成员",
            subtitle = "查看 OKR 与复盘"
        ) {
            OrgTeamActivity.start(requireContext())
        }

        bindMenu(
            binding = mDataBinding.menuEvaluation,
            icon = R.drawable.ic_personal_archive,
            title = "评价记录",
            subtitle = "工单评价历史"
        ) {
            ARouter.getInstance()
                .build("/hiddendanger/EvaluationRecordActivity")
                .navigation()
        }

        bindMenu(
            binding = mDataBinding.menuSetting,
            icon = R.drawable.ic_personal_setting,
            title = "检查更新"
        ) {
            com.fuusy.common.update.AppUpdateChecker.checkManually(
                activity = requireActivity(),
                smallIcon = R.drawable.ic_personal_setting
            )
        }
    }

    private fun setupStatCards() {
        mDataBinding.cardStatCompleted.setOnClickListener { openCompletedOrders() }
        mDataBinding.cardStatRating.setOnClickListener {
            ARouter.getInstance()
                .build("/hiddendanger/EvaluationRecordActivity")
                .navigation()
        }
    }

    private fun openCompletedOrders() {
        ARouter.getInstance()
            .build("/project/HistoryOrderActivity")
            .withString(
                com.fuusy.project.ui.activity.HistoryOrderActivity.EXTRA_LIST_MODE,
                com.fuusy.project.ui.activity.HistoryOrderActivity.MODE_COMPLETED
            )
            .navigation()
    }

    private fun bindMenu(
        binding: com.fuusy.hiddendanger.databinding.ItemPersonalMenuBinding,
        icon: Int,
        title: String,
        subtitle: String? = null,
        onClick: () -> Unit
    ) {
        binding.ivIcon.setImageResource(icon)
        binding.tvTitle.text = title
        if (subtitle.isNullOrBlank()) {
            binding.tvSubtitle.visibility = android.view.View.GONE
        } else {
            binding.tvSubtitle.visibility = android.view.View.VISIBLE
            binding.tvSubtitle.text = subtitle
        }
        binding.rootMenuItem.setOnClickListener { onClick() }
    }

    /** 预留系统状态栏高度，避免头像/功能菜单顶到屏幕最上方 */
    private fun applyStatusBarPadding() {
        val extraTopPx = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(mDataBinding.personalContent) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop + extraTopPx)
            insets
        }
        ViewCompat.requestApplyInsets(mDataBinding.personalContent)
    }

    override fun onResume() {
        super.onResume()
        mViewModel.loadData()
    }
}
