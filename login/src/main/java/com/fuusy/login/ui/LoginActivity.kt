package com.fuusy.login.ui

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.common.network.net.IStateObserver
import com.fuusy.common.support.Constants
import com.fuusy.common.support.Constants.Companion.KEY_LIVEDATA_BUS_LOGIN
import com.fuusy.common.support.Constants.Companion.SP_KEY_REMEMBER_PASSWORD
import com.fuusy.common.support.Constants.Companion.SP_KEY_SAVED_PASSWORD
import com.fuusy.common.support.Constants.Companion.SP_KEY_SAVED_USERNAME
import com.fuusy.common.support.Constants.Companion.SP_KEY_USER_INFO_NAME
import com.fuusy.common.support.LiveDataBus
import com.fuusy.common.utils.SpUtils
import com.fuusy.common.utils.LoadingStatus
import com.fuusy.common.utils.LoadingUtils
import com.fuusy.login.R
import com.fuusy.login.databinding.ActivityLoginBinding
import com.fuusy.login.viewmodel.LoginViewModel
import com.fuusy.service.repo.LoginResp
import com.fuusy.service.repo.DbHelper
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.os.Handler
import android.os.Looper
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.UserIdProvider

@Route(path = Constants.PATH_LOGIN)
class LoginActivity : BaseVmActivity<ActivityLoginBinding>() {

    private val mViewModel: LoginViewModel by viewModel()
    private var isPasswordVisible = false
    private var isRememberChecked = true
    private var clickCount = 0
    private var lastClickTime = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val clickRunnable = Runnable { resetClickCount() }

    override fun initData() {
        setupStatusBar()
        loadRememberPasswordState()
        initListener()
        registerObserve()
        updateLoginButtonState()
        updateRememberCheckboxState()
    }

    private fun loadRememberPasswordState() {
        isRememberChecked = SpUtils.getBoolean(SP_KEY_REMEMBER_PASSWORD, true)
        if (!isRememberChecked) return
        mBinding?.run {
            SpUtils.getString(SP_KEY_SAVED_USERNAME)?.takeIf { it.isNotBlank() }?.let {
                etUserName.setText(it)
            }
            SpUtils.getString(SP_KEY_SAVED_PASSWORD)?.takeIf { it.isNotBlank() }?.let {
                etPassword.setText(it)
            }
        }
    }

    private fun saveRememberCredentials(username: String, password: String) {
        SpUtils.put(SP_KEY_REMEMBER_PASSWORD, isRememberChecked)
        if (isRememberChecked) {
            SpUtils.put(SP_KEY_SAVED_USERNAME, username)
            SpUtils.put(SP_KEY_SAVED_PASSWORD, password)
        } else {
            SpUtils.removeValue(SP_KEY_SAVED_USERNAME)
            SpUtils.removeValue(SP_KEY_SAVED_PASSWORD)
        }
    }

    private fun setupStatusBar() {
        window.statusBarColor = Color.WHITE
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
    }

    private fun registerObserve() {
        // 监听 loading 状态
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

        mViewModel.loginLiveData.observe(this, object : IStateObserver<LoginResp>(null) {
            override fun onDataChange(data: LoginResp?) {
                mBinding?.run {
                    saveRememberCredentials(
                        etUserName.text.toString().trim(),
                        etPassword.text.toString().trim()
                    )
                }
                showToast("登录成功")
                SpUtils.put("is_logged_in", true)
                SpUtils.put(SP_KEY_USER_INFO_NAME, data?.displayName())
                SpUtils.put("user_name", data?.displayName() ?: "")
                SpUtils.put("user_username", data?.username ?: "")
                SpUtils.put("user_company", data?.company ?: "")
                SpUtils.put("user_department", data?.department ?: "")
                SpUtils.put("user_dept_id", data?.deptId ?: 0L)
                com.fuusy.common.auth.DeptRoleHelper.setDeptLeader(data?.deptLeader == true)

                data?.let { userData ->
                    UserIdProvider.update(userData.id.toLong())
                    DbHelper.insertUserInfo(this@LoginActivity, userData)
                }

                LiveDataBus.get().with(KEY_LIVEDATA_BUS_LOGIN).postValue(data)
                ARouter.getInstance().build("/project/ProjectDetailActivity").navigation()
                finish()
            }

            override fun onReload(v: View?) {
                mBinding?.run {
                    mViewModel.login(etUserName.text.toString(), etPassword.text.toString())
                }
            }

        })
    }

    private fun initListener() {
        mBinding?.run {
            // Logo 连续点击或长按，弹出服务器配置
            ivLogo.isClickable = true
            ivLogo.isFocusable = true
            ivLogo.setOnClickListener { handleLogoClick() }
            ivLogo.setOnLongClickListener {
                showIpConfigDialog()
                true
            }
            tvSystemName.setOnClickListener { handleLogoClick() }
            tvSystemName.setOnLongClickListener {
                showIpConfigDialog()
                true
            }

            // 输入监听
            val textWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateLoginButtonState()
                }
            }
            etUserName.addTextChangedListener(textWatcher)
            etPassword.addTextChangedListener(textWatcher)

            // 登录按钮
            btLogin.setOnClickListener {
                val username = etUserName.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (username.isEmpty()) {
                    showToast("请输入工号或手机号")
                    return@setOnClickListener
                }
                if (password.isEmpty()) {
                    showToast("请输入密码")
                    return@setOnClickListener
                }
                if (!ServerConfig.isMockData) {
                    mViewModel.login(username, password)
                } else {
                    saveRememberCredentials(username, password)
                    SpUtils.put("is_logged_in", true)
                    UserIdProvider.update(1L)
                    ARouter.getInstance().build("/project/ProjectDetailActivity").navigation()
                    finish()
                }
            }

            // 密码可见性切换
            ivPasswordToggle.setOnClickListener {
                isPasswordVisible = !isPasswordVisible
                if (isPasswordVisible) {
                    etPassword.inputType = InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                    ivPasswordToggle.setImageResource(R.mipmap.ic_public_password_visible)
                } else {
                    etPassword.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    ivPasswordToggle.setImageResource(R.mipmap.ic_public_password_invisible)
                }
                etPassword.setSelection(etPassword.text.length)
            }

            // 记住密码
            llRememberPwd.setOnClickListener {
                isRememberChecked = !isRememberChecked
                SpUtils.put(SP_KEY_REMEMBER_PASSWORD, isRememberChecked)
                if (!isRememberChecked) {
                    SpUtils.removeValue(SP_KEY_SAVED_PASSWORD)
                }
                updateRememberCheckboxState()
            }

            // 忘记密码
            tvForgetPwd.setOnClickListener {
                ARouter.getInstance().build(Constants.PATH_FORGOT_PASSWORD).navigation()
            }

            tvGoRegister.setOnClickListener {
                ARouter.getInstance().build(Constants.PATH_REGISTER).navigation()
            }
        }
    }

    private fun updateRememberCheckboxState() {
        mBinding?.run {
            if (isRememberChecked) {
                flCheckbox.setBackgroundResource(R.drawable.login_checkbox_checked)
                ivCheck.visibility = View.VISIBLE
            } else {
                flCheckbox.setBackgroundResource(R.drawable.login_checkbox_unchecked)
                ivCheck.visibility = View.GONE
            }
        }
    }

    private fun handleLogoClick() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime > 2000) clickCount = 0
        clickCount++
        lastClickTime = currentTime
        handler.removeCallbacks(clickRunnable)
        if (clickCount >= 3) {
            showIpConfigDialog()
            clickCount = 0
        } else {
            val remain = 3 - clickCount
            showToast("再点${remain}次打开服务器配置（或长按 Logo）")
            handler.postDelayed(clickRunnable, 2000)
        }
    }

    private fun resetClickCount() {
        clickCount = 0
    }

    private fun showIpConfigDialog() {
        val dialog = IpConfigDialog(this)
        dialog.show()
    }

    private fun updateLoginButtonState() {
        mBinding?.run {
            val username = etUserName.text.toString().trim()
            val password = etPassword.text.toString().trim()
            btLogin.isEnabled = username.isNotEmpty() && password.isNotEmpty()
        }
    }

    override fun getLayoutId(): Int = R.layout.activity_login
}
