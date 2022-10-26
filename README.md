# Agora Metabolt Demo
agorameta结合了aroga rtc框架与JOYY Metabolt，借助agora音频/视频通道实现Metabolt虚拟人表情随动、跟随音乐跳舞、跟随音乐舞动节拍等行为。

# Quick Start

## Obtain an App Id
To build and run the sample application, get an App Id:

- Create a developer account at agora.io. Once you finish the signup process, you will be redirected to the Dashboard.

- Navigate in the Dashboard tree on the left to Projects > Project List.

- Save the App Id from the Dashboard for later use.

- Save the App Certificate from the Dashboard for later use.

- Generate a temp Access Token (valid for 24 hours) from dashboard page with given channel name, save for later use.

- Open agorameta and edit the app/src/main/res/values/string-config.xml file. Update YOUR APP ID with your App Id, update YOUR APP CERTIFICATE with the main app certificate from dashboard, and update YOUR ACCESS TOKEN with the temp Access Token generated from dashboard. Note you can leave the token and certificate variable null if your project has not turned on security token.

```java
// Agora APP ID.
<string name="agora_app_id" translatable="false">YOUR APP ID</string>
// Agora APP Certificate. If the project does not have certificates enabled, leave this field blank.
<string name="agora_app_certificate" translatable="false">YOUR APP CERTIFICATE</string>
// Temporary Access Token. If agora_app_certificate is configured, this field will be invalid.
<string name="agora_access_token" translatable="false">YOUR ACCESS TOKEN</string>
You are all set. Now connect your Android device and run the project.
```

# agorameta演示
demo初始化界面如下图
![image](https://user-images.githubusercontent.com/18079722/197968992-04d9495b-d085-417e-8ad0-a57465dd133b.png)
有四块区域，其中左上角（位置1）显示本地虚拟人画面、右上角（位置2）显示本地摄像头预览画面、左下角（位置3）为远端虚拟人画面、右下角（位置4）为远端视频画面。

- 配置uid与channel
输入uid以及agora获取token的channel id

- 请求token
点击请求token，获取JOYY Metabolt SDK token

- 进频道
点击进频道，待Metabolt SDK回调MTB_STATE_INIT_SUCCESS之后，可以看到位置1显示虚拟人画面，此时虚拟人显示头像模式MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_HEAD，此时默认已经开启了表情随动。虚拟人口型会根据本地采集声音而变动，头像跟面部表情会跟随摄像头采集视频数据随动。

- 开启/停止舞蹈
舞蹈跟节拍需要调整虚拟人显示全身模式MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_WHOLE，本地会播放音乐，可以看到本地虚拟人伴随音乐跳舞。再点击停止跳舞，虚拟人停止舞蹈。

- 开始/停止节拍
开始节拍可以看到本地虚拟人伴随音乐挥舞手臂节拍，再点击停止跳舞，虚拟人停止挥舞节拍。

