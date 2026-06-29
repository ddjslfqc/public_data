package com.fuusy.login.ui

import androidx.appcompat.app.AlertDialog
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.common.network.net.IStateObserver
import com.fuusy.common.support.Constants
import com.fuusy.common.utils.LoadingStatus
import com.fuusy.common.utils.LoadingUtils
import com.fuusy.login.R
import com.fuusy.login.databinding.ActivityRegisterBinding
import com.fuusy.login.viewmodel.LoginViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

@Route(path = Constants.PATH_REGISTER)
class RegisterActivity : BaseVmActivity<ActivityRegisterBinding>() {
    private val mViewModel: LoginViewModel by viewModel()
    private var selectedDeptId: Long = 0
    private var selectedDeptLabel: String = ""

    override fun initData() {
        initToolbar()
        mViewModel.loadDeptList()
        observeRegisterState()

        mBinding?.run {
            rowDept.setOnClickListener { showDeptPicker() }
            btRegister.setOnClickListener {
                val username = etUserName.text.toString().trim()
                val password = etPassword.text.toString()
                val rePassword = etIvPasswordSure.text.toString()
                val nickName = etNickName.text.toString().trim()

                when {
                    username.isBlank() -> showToast("请输入账号")
                    nickName.isBlank() -> showToast("请输入姓名")
                    password.isBlank() -> showToast("请输入密码")
                    password.length < 6 -> showToast("密码至少 6 位")
                    password != rePassword -> showToast("两次密码不一致")
                    selectedDeptId <= 0 -> showToast("请选择部门")
                    else -> mViewModel.register(username, password, rePassword, nickName, selectedDeptId)
                }
            }
        }

        mViewModel.deptOptions.observe(this) { list ->
            if (list.isNotEmpty() && selectedDeptId <= 0) {
                selectedDeptId = list.first().first
                selectedDeptLabel = list.first().second
                mBinding?.tvDept?.text = selectedDeptLabel
            }
        }
    }

    private fun observeRegisterState() {
        mViewModel.loadingStatus.observe(this) { status ->
            when (status) {
                is LoadingStatus.Idle -> LoadingUtils.hideLoading()
                is LoadingStatus.Loading -> LoadingUtils.showLoading(this)
                is LoadingStatus.Success -> LoadingUtils.hideLoading()
                is LoadingStatus.Error -> {
                    LoadingUtils.hideLoading()
                    showToast(status.message)
                }
            }
        }

        mViewModel.registerLiveData.observe(this, object : IStateObserver<Boolean>(null) {
            override fun onDataChange(data: Boolean?) {
                showToast("注册成功，请登录")
                finish()
            }

            override fun onError(e: Throwable?) {
                showToast(e?.message ?: "注册失败")
            }
        })
    }

    private fun showDeptPicker() {
        val list = mViewModel.deptOptions.value.orEmpty()
        if (list.isEmpty()) {
            showToast("部门列表加载中，请稍后")
            mViewModel.loadDeptList()
            return
        }
        val labels = list.map { it.second }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("选择部门")
            .setItems(labels) { dialog, which ->
                selectedDeptId = list[which].first
                selectedDeptLabel = list[which].second
                mBinding?.tvDept?.text = selectedDeptLabel
                dialog.dismiss()
            }
            .show()
    }

    override fun getLayoutId(): Int = R.layout.activity_register

    private fun initToolbar() {
        mBinding?.run {
            setToolbarBackIcon(llToolbarLogin.ivBack, com.fuusy.common.R.drawable.ic_back_clear)
            setToolbarTitle(llToolbarLogin.tvTitle, "注册")
            llToolbarLogin.ivBack.setOnClickListener { finish() }
        }
    }
}
