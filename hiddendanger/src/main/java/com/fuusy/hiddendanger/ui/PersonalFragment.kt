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

        mViewModel.logout.observe(viewLifecycleOwner, Observer {
            if (it) {
                showToast("退出登录成功")
                ARouter.getInstance().build("/login/LoginActivity").navigation()
                activity?.finish()
            } else {
                showToast("退出登录失败")
            }
        })

        setupMenuItems()
        setupTopActions()

        mDataBinding.btnLogout.setOnClickListener {
            mViewModel.logout()
        }

        mViewModel.loadData()
    }

    private fun setupMenuItems() {
        bindMenu(
            binding = mDataBinding.menuArchive,
            icon = R.drawable.ic_personal_archive,
            title = "我的档案"
        ) {
            ARouter.getInstance()
                .build("/hiddendanger/MyArchiveActivity")
                .navigation()
        }

        bindMenu(
            binding = mDataBinding.menuGoal,
            icon = R.drawable.ic_personal_goal,
            title = "我的目标"
        ) {
            ARouter.getInstance()
                .build("/hiddendanger/MyGoalsActivity")
                .navigation()
        }

        bindMenu(
            binding = mDataBinding.menuEvaluation,
            icon = R.drawable.ic_personal_evaluation,
            title = "评价记录"
        ) {
            ARouter.getInstance()
                .build("/hiddendanger/EvaluationRecordActivity")
                .navigation()
        }

        bindMenu(
            binding = mDataBinding.menuSetting,
            icon = R.drawable.ic_personal_setting,
            title = "设置"
        ) {
            showToast("设置即将上线")
        }
    }

    private fun bindMenu(
        binding: com.fuusy.hiddendanger.databinding.ItemPersonalMenuBinding,
        icon: Int,
        title: String,
        onClick: () -> Unit
    ) {
        binding.ivIcon.setImageResource(icon)
        binding.tvTitle.text = title
        binding.rootMenuItem.setOnClickListener { onClick() }
    }

    private fun setupTopActions() {
        mDataBinding.btnTopSetting.setOnClickListener {
            showToast("设置即将上线")
        }
        mDataBinding.btnTopLink.setOnClickListener {
            showToast("关联功能即将上线")
        }
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
