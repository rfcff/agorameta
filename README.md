# Joyy Metabolt Demo
JoyyMeta结合了aroga、rtc框架与JOYY Metabolt，借助agora、trtc音频/视频通道实现Metabolt虚拟人表情随动、跟随音乐跳舞、跟随音乐舞动节拍等行为。

# JoyyMeta演示

demo初始化界面如下图<br>![trtc_main](https://user-images.githubusercontent.com/18079722/229682779-670b85c4-d5f9-4801-8c20-7ec7040e9977.jpg)<br>
有四块区域，其中左上角（位置1）显示本地虚拟人画面、右上角（位置2）显示本地摄像头预览画面、左下角（位置3）为远端虚拟人画面、右下角（位置4）为远端视频画面。连麦画面如下图<br>![trtc_lianmai](https://user-images.githubusercontent.com/18079722/229683705-fca82c97-5566-47c0-80a6-d1d37ce491c6.jpg)


- handup

选择虚拟人节拍方式，目前支持：挥手、跳舞以及唱歌

- female_role

选择默认角色性别，目前支持：female以及male

- 选择音乐

k-pop用于选择伴随音乐，用于节拍以及跳舞场景

- 显示模式

当前支持三种模式选择：全身、半身以及头像

- 驱动模式

当前支持音频以及视频驱动

- 通道选择

目前支持声网Agora以及腾讯TRTC两个通道

- 配置uid与channel

输入uid以及获取token的channel id

- 请求token

点击请求token，获取JOYY Metabolt SDK token

- 进频道

点击进频道，待Metabolt SDK回调MTB_STATE_INIT_SUCCESS之后，可以看到位置1显示虚拟人画面，此时虚拟人显示头像模式MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_HEAD，此时默认已经开启了表情随动。虚拟人口型会根据本地采集声音而变动，头像跟面部表情会跟随摄像头采集视频数据随动。

- 开启/停止舞蹈

舞蹈跟节拍需要调整虚拟人显示全身模式MetaBoltTypes.MTBAvatarViewType.MTB_AVATAR_VIEW_TYPE_WHOLE，本地会播放音乐，可以看到本地虚拟人伴随音乐跳舞。再点击停止跳舞，虚拟人停止舞蹈。

- 开始/停止节拍

开始节拍可以看到本地虚拟人伴随音乐挥舞手臂节拍，再点击停止跳舞，虚拟人停止挥舞节拍。


