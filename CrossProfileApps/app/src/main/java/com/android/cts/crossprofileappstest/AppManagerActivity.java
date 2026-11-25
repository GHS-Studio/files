/*
 * CrossProfileApps - 禁用系统应用界面
 *
 * 基于 AOSP CTS android-cts-10.0_r16 的 CtsCrossProfileAppsTests 扩展：
 *   - 列出设备上所有系统应用（ApplicationInfo.FLAG_SYSTEM）
 *   - 调用 PackageManager.setApplicationEnabledSetting 禁用/启用
 *   - 顶部展示当前 CrossProfileApps.getTargetUserProfiles() 状态
 *
 * 注意：对"其他应用/系统应用"调用 setApplicationEnabledSetting 需要本应用具备
 *   CHANGE_COMPONENT_ENABLED_STATE 且为 Profile Owner / 设备所有者，否则运行时会抛
 *   SecurityException。普通安装时仅能查看状态，无法真正禁用。
 */
package com.android.cts.crossprofileappstest;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppManagerActivity extends Activity {

    private final List<AppInfo> mApps = new ArrayList<>();
    private AppAdapter mAdapter;
    private TextView mHeader;
    private PackageManager mPm;
    private CrossProfileApps mCpa;

    static class AppInfo {
        final String label;
        final String pkg;
        boolean enabled;

        AppInfo(String label, String pkg, boolean enabled) {
            this.label = label;
            this.pkg = pkg;
            this.enabled = enabled;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPm = getPackageManager();
        mCpa = getSystemService(CrossProfileApps.class);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        mHeader = new TextView(this);
        mHeader.setPadding(dp(16), dp(16), dp(16), dp(12));
        mHeader.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        root.addView(mHeader, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ListView list = new ListView(this);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        mAdapter = new AppAdapter();
        list.setAdapter(mAdapter);

        refreshHeader();
        loadAppsAsync();
    }

    private void refreshHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("CrossProfileApps · 禁用系统应用 (Android 10)\n");
        sb.append("当前可交互的目标 profile 数: ");
        try {
            List<UserHandle> profiles = mCpa.getTargetUserProfiles();
            sb.append(profiles == null ? 0 : profiles.size());
        } catch (Throwable t) {
            sb.append("N/A (").append(t.getClass().getSimpleName()).append(")");
        }
        sb.append("\n\n提示: 禁用系统应用需将本应用设为 Profile Owner / 设备所有者，");
        sb.append("否则系统会抛 SecurityException。");
        mHeader.setText(sb.toString());
    }

    private void loadAppsAsync() {
        new Thread(() -> {
            final List<AppInfo> loaded = new ArrayList<>();
            try {
                List<ApplicationInfo> infos = mPm.getInstalledApplications(
                        PackageManager.GET_META_DATA);
                for (ApplicationInfo ai : infos) {
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) == 0) {
                        continue;
                    }
                    int state = mPm.getApplicationEnabledSetting(ai.packageName);
                    boolean enabled = state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
                    loaded.add(new AppInfo(
                            String.valueOf(mPm.getApplicationLabel(ai)),
                            ai.packageName, enabled));
                }
                Collections.sort(loaded, new Comparator<AppInfo>() {
                    @Override
                    public int compare(AppInfo a, AppInfo b) {
                        return a.label.compareToIgnoreCase(b.label);
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
            runOnUiThread(() -> {
                mApps.clear();
                mApps.addAll(loaded);
                mAdapter.notifyDataSetChanged();
            });
        }).start();
    }

    private void toggle(AppInfo info) {
        int target = info.enabled
                ? PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                : PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        try {
            mPm.setApplicationEnabledSetting(info.pkg, target, 0);
            info.enabled = (target == PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
            mAdapter.notifyDataSetChanged();
            Toast.makeText(this,
                    (info.enabled ? "已启用: " : "已禁用: ") + info.pkg,
                    Toast.LENGTH_SHORT).show();
        } catch (SecurityException se) {
            Toast.makeText(this,
                    "无权限禁用系统应用 (需 Profile Owner/设备所有者):\n" + se.getMessage(),
                    Toast.LENGTH_LONG).show();
        } catch (Throwable t) {
            Toast.makeText(this, "操作失败: " + t.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    class AppAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mApps.size();
        }

        @Override
        public Object getItem(int position) {
            return mApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            AppInfo info = mApps.get(position);
            LinearLayout row;
            if (convertView == null) {
                row = new LinearLayout(AppManagerActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(16), dp(10), dp(16), dp(10));

                LinearLayout textCol = new LinearLayout(AppManagerActivity.this);
                textCol.setOrientation(LinearLayout.VERTICAL);
                textCol.setTag("text");
                TextView label = new TextView(AppManagerActivity.this);
                label.setTag("lab");
                label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                TextView sub = new TextView(AppManagerActivity.this);
                sub.setTag("sub");
                sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                sub.setTextColor(Color.GRAY);
                textCol.addView(label);
                textCol.addView(sub);
                row.addView(textCol, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                Button btn = new Button(AppManagerActivity.this);
                btn.setTag("btn");
                btn.setMinWidth(dp(72));
                row.addView(btn, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                row = (LinearLayout) convertView;
            }

            LinearLayout textCol = (LinearLayout) row.findViewWithTag("text");
            ((TextView) textCol.findViewWithTag("lab")).setText(info.label);
            ((TextView) textCol.findViewWithTag("sub")).setText(
                    info.pkg + (info.enabled ? "  ·  已启用" : "  ·  已禁用"));
            Button btn = (Button) row.findViewWithTag("btn");
            btn.setText(info.enabled ? "禁用" : "启用");
            btn.setOnClickListener(v -> toggle(info));
            return row;
        }
    }
}
