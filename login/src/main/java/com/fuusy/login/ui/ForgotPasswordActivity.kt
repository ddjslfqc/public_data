package com.fuusy.login.ui

import android.text.InputType
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.common.network.net.IStateObserver
import com.fuusy.common.support.Constants
import com.fuusy.common.utils.LoadingStatus
import com.fuusy.common.utils.LoadingUtils
import com.fuusy.login.R
import com.fuusy.login.databinding.ActivityForgotPasswordBinding
import com.fuusy.login.viewmodel.LoginViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

@Route(path = Constants.PATH_FORGOT_PASSWORD)
class ForgotPasswordActivity : BaseVmActivity<ActivityForgotPasswordBinding>() {

    private val mViewModel: LoginViewModel by viewModel()
    private var passwordVisible = false
    private var passwordSureVisible = false

    override fun initData() {
        observeResetState()
        setupPasswordToggles()

        mBinding?.run {
            btnBack.setOnClickListener { finish() }
            tvGoLogin.setOnClickListener { finish() }
            btReset.setOnClickListener {
                val username = etUserName.text.toString().trim()
                val nickName = etNickName.text.toString().trim()
                val password = etPassword.text.toString()
                val rePassword = etIvPasswordSure.text.toString()

                when {
                    username.isBlank() -> showToast("请输入工号或手机号")
                    nickName.isBlank() -> showToast("请输入姓名")
                    password.isBlank() -> showToast("请输入新密码")
                    password.length < 6 -> showToast("密码至少 6 位")
                    password != rePassword -> showToast("两次密码不一致")
                    else -> mViewModel.resetPassword(username, nickName, password)
                }
            }
        }
    }

    private fun setupPasswordToggles() {
        mBinding?.run {
            ivPasswordToggle.setOnClickListener {
                passwordVisible = !passwordVisible
                if (passwordVisible) {
                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    ivPasswordToggle.setImageResource(R.mipmap.ic_public_password_visible)
                } else {
                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    ivPasswordToggle.setImageResource(R.mipmap.ic_public_password_invisible)
                }
                etPassword.setSelection(etPassword.text.length)
            }

            ivPasswordSureToggle.setOnClickListener {
                passwordSureVisible = !passwordSureVisible
                if (passwordSureVisible) {
                    etIvPasswordSure.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    ivPasswordSureToggle.setImageResource(R.mipmap.ic_public_password_visible)
                } else {
                    etIvPasswordSure.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    ivPasswordSureToggle.setImageResource(R.mipmap.ic_public_password_invisible)
                }
                etIvPasswordSure.setSelection(etIvPasswordSure.text.length)
            }
        }
    }

    private fun observeResetState() {
        mViewModel.loadingStatus.observe(this) { status ->
            when (status) {
                is LoadingStatus.Idle -> LoadingUtils.hideLoading()
                is LoadingStatus.Loading -> LoadingUtils.showLoading(this)
                is LoadingStatus.Success -> LoadingUtils.hideLoading()
                is LoadingStatus.Error -> {
                    LoadingUtils.hideLoading()
                    showErrorToast(status.message)
                }
            }
        }

        mViewModel.resetPasswordLiveData.observe(this, object : IStateObserver<Boolean>(null) {
            override fun onDataChange(data: Boolean?) {
                showToast("密码重置成功，请登录")
                finish()
            }
        })
    }

    override fun getLayoutId(): Int = R.layout.activity_forgot_password
}
