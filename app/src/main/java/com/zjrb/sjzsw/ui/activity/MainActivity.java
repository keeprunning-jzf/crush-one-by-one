package com.zjrb.sjzsw.ui.activity;

import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.view.View;

import com.zjrb.sjzsw.R;
import com.zjrb.sjzsw.databinding.AcMainBinding;
import com.zjrb.sjzsw.ui.fragment.NetFragment;


/**
 * @author jinzifu
 */
public class MainActivity extends BaseActivity<AcMainBinding> {
    @Override
    protected int getLayoutId() {
        return R.layout.ac_main;
    }

    @Override
    protected void init(Bundle savedInstanceState) {
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_id, new NetFragment()).commit();
        t.topBar.titleText.setText("网络架构");
    }
}