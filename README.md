# 蓝牙配网 App（BleProvision）

通过 BLE 给硬件设备配网的安卓 App：扫描蓝牙 → 选择设备 → 扫描热点 → 输入密码 → 通过蓝牙下发 WiFi 信息。

当前为第一阶段：验证编译流程，UI 仅为占位。

## 打包方式（无需本地安装任何环境）

项目已配置 GitHub Actions（`.github/workflows/build.yml`），推送到 GitHub 后自动编译，在 Actions 页面的 Artifacts 中下载 `app-debug-apk`。

## 上传到 GitHub

在本目录下执行：

```bash
git init
git add .
git commit -m "init: 项目骨架"
git branch -M main
git remote add origin https://github.com/<你的用户名>/<仓库名>.git
git push -u origin main
```

上传后进入仓库的 Actions 页面查看编译进度，完成后在对应运行记录底部下载 APK。
