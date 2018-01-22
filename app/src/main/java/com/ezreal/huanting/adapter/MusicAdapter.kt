package com.ezreal.huanting.adapter

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.RelativeLayout
import android.widget.TextView
import cn.hotapk.fastandrutils.utils.FToastUtils
import com.ezreal.huanting.R
import com.ezreal.huanting.activity.NowPlayingActivity
import com.ezreal.huanting.bean.MusicBean
import com.ezreal.huanting.bean.MusicListBean
import com.ezreal.huanting.event.MusicPlayAction
import com.ezreal.huanting.event.PlayActionEvent
import com.ezreal.huanting.helper.GlobalMusicList
import com.ezreal.huanting.helper.MusicDataHelper
import com.ezreal.huanting.utils.Constant
import org.greenrobot.eventbus.EventBus


/**
 * 音乐列表适配器
 * Created by wudeng on 2017/11/27.
 */

class MusicAdapter(private val mContext: Context, private val listId: Long,
                   private val mList: List<MusicBean>)
    : RecycleViewAdapter<MusicBean>(mContext, mList) {

    init {
        if (listId == GlobalMusicList.getListId() && mList.isNotEmpty()) {
            mList[GlobalMusicList.getCurrentIndex()].status = Constant.PLAY_STATUS_PLAYING
        }
    }

    override fun setItemLayoutId(position: Int): Int = R.layout.item_music

    override fun bindView(holder: RViewHolder, position: Int) {
        holder.setText(R.id.mTvSongTitle, mList[position].musicTitle!!)
        holder.setText(R.id.mTvArtist, mList[position].artist!!)
        holder.setText(R.id.tv_album, mList[position].album!!)
        holder.getImageView(R.id.iv_play_status)?.visibility = View.GONE
        when {
            mList[position].status == Constant.PLAY_STATUS_PLAYING -> {
                holder.setImageResource(R.id.iv_play_status, R.mipmap.play)
                holder.getImageView(R.id.iv_play_status)?.visibility = View.VISIBLE
            }
            mList[position].status == Constant.PLAY_STATUS_PAUSE -> {
                holder.setImageResource(R.id.iv_play_status, R.mipmap.pause)
                holder.getImageView(R.id.iv_play_status)?.visibility = View.VISIBLE
            }
        }
        val view = holder.convertView.findViewById<ImageView>(R.id.iv_item_menu)
        view.setOnClickListener {
            showPopupWindow(view, mList[position])
        }
    }

    private fun showPopupWindow(view: View, music: MusicBean) {
        val rootView = LayoutInflater.from(mContext)
                .inflate(R.layout.popu_music_menu, null, false)
        val popupWindow = PopupWindow(rootView, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

        rootView.findViewById<TextView>(R.id.mTvMusicTitle).text = music.musicTitle
        rootView.findViewById<TextView>(R.id.mTvArtist).text = music.artist
        rootView.findViewById<TextView>(R.id.mTvAlbum).text = music.album
        rootView.findViewById<RelativeLayout>(R.id.mLayoutPlayNext).setOnClickListener {
            playMusicNext(music)
            popupWindow.dismiss()
        }
        rootView.findViewById<RelativeLayout>(R.id.mLayoutAdd2List).setOnClickListener {
            add2MusicList(music)
            popupWindow.dismiss()
        }
        rootView.findViewById<RelativeLayout>(R.id.mLayoutDelete).setOnClickListener {
            // TODO  提示是否删除
            popupWindow.dismiss()
        }
        popupWindow.isOutsideTouchable = true
        popupWindow.animationStyle = R.style.MyPopupStyle
        popupWindow.setOnDismissListener {
            lightOn()
        }
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        popupWindow.showAtLocation(view, Gravity.START or Gravity.BOTTOM,
                0, -location[1])
        lightOff()
    }

    private fun add2MusicList(music: MusicBean) {
        MusicDataHelper.loadMusicListAll(object : MusicDataHelper.OnListLoadListener {
            override fun loadSuccess(list: List<MusicListBean>) {
                showSelectList(music, list)
            }

            override fun loadFailed(message: String) {
                FToastUtils.init().show("获取歌单列表失败：" + message)
            }

        })
    }


    private fun playMusicNext(music: MusicBean) {
        val currentIndex = GlobalMusicList.getCurrentIndex()
        when (GlobalMusicList.getListId()) {
        // 当前播放列表为空,创建临时播放列表并播放该歌曲,打开播放页
            -1L -> {
                music.status = Constant.PLAY_STATUS_PLAYING
                val tempList = listOf(music)
                GlobalMusicList.updateList(Constant.TEMP_MUSIL_LIST_ID, tempList)
                GlobalMusicList.updatePlayIndex(0)
                EventBus.getDefault().post(PlayActionEvent(MusicPlayAction.PLAY,-1))
                mContext.startActivity(Intent(mContext, NowPlayingActivity::class.java))
            }

        // 在原播放列表中操作
            listId -> {
                val itemIndex = GlobalMusicList.getNowPlayingList().indexOf(music)
                if (music.status == Constant.PLAY_STATUS_PLAYING) {
                    return
                }
                // 未在播放，并且已经在下一首播放位置，什么都不做
                if (itemIndex == currentIndex + 1) {
                    return
                }
                // 否则，改变播放列表歌曲位置，将该歌曲移动到下一首播放的位置
                changePlayListIndex(music, currentIndex, itemIndex)
            }

        // 其他情况
            else -> {
                // 判断要加入播放列表的歌曲是否已存在于列表内
                val first = GlobalMusicList.getNowPlayingList().firstOrNull { it.musicId == music.musicId }

                // 已存在,则判断播放状态和所在位置
                if (first != null) {
                    // 找到歌曲在列表中的位置
                    val itemIndex = GlobalMusicList.getNowPlayingList().indexOf(first)
                    // 已存在且已经在播放，什么都不做
                    if (first.status == Constant.PLAY_STATUS_PLAYING) {
                        return
                    }
                    // 已存在，未在播放，并且已经在下一首播放位置，什么都不做
                    if (itemIndex == currentIndex + 1) {
                        return
                    }
                    // 否则，改变播放列表歌曲位置，将该歌曲移动到下一首播放的位置
                    changePlayListIndex(music, currentIndex, itemIndex)
                }
                // 不存在，添加该歌曲到播放列表中，且在当前的播放歌曲的后一位置
                else {
                    GlobalMusicList.getNowPlayingList().add(currentIndex + 1, music)
                }
            }
        }
    }

    private fun changePlayListIndex(music: MusicBean, currentIndex: Int, itemIndex: Int) {
        val list = GlobalMusicList.getNowPlayingList()
        if (itemIndex < currentIndex) {
            list.remove(music)
            list.add(currentIndex, music)
            GlobalMusicList.updatePlayIndex(currentIndex - 1)
        }else {
            list.remove(music)
            list.add(currentIndex + 1, music)
        }
    }

    fun checkPlaySong(musicPosition: Int, viewPosition: Int) {
        // 如果当前播放列表不是本地音乐列表，更新播放列表，并切换播放歌曲
        if (GlobalMusicList.getListId() != listId) {
            // 恢复旧播放列表歌曲状态
            if (!GlobalMusicList.getNowPlayingList().isEmpty()
                    && GlobalMusicList.getCurrentIndex() != -1) {
                GlobalMusicList.getNowPlayingList()[GlobalMusicList.getCurrentIndex()].status =
                        Constant.PLAY_STATUS_NORMAL
            }
            // 切换播放歌曲
            playNewMusic(musicPosition, viewPosition)
            return
        }

        // 当前播放列表是本地音乐列表，若点击了当前播放的 item 打开音乐播放页，否则切换歌曲
        if (GlobalMusicList.getCurrentIndex() == musicPosition) {
            mContext.startActivity(Intent(mContext, NowPlayingActivity::class.java))
            return
        }

        // 将原来播放的 item 状态重置
        if (GlobalMusicList.getCurrentIndex() != -1) {
            val oldPosition = GlobalMusicList.getCurrentIndex()
            mList[oldPosition].status = Constant.PLAY_STATUS_NORMAL
            notifyItemChanged(oldPosition + (viewPosition - musicPosition))
        }

        // 更新播放歌曲状态
        playNewMusic(musicPosition, viewPosition)
    }

    private fun playNewMusic(musicPosition: Int, viewPosition: Int) {
        GlobalMusicList.updateList(listId, mList)
        GlobalMusicList.updatePlayIndex(musicPosition)
        mList[musicPosition].status = Constant.PLAY_STATUS_PLAYING
        notifyItemChanged(viewPosition)
        EventBus.getDefault().post(PlayActionEvent(MusicPlayAction.PLAY,-1))
    }

    private fun showSelectList(music: MusicBean, list: List<MusicListBean>) {
        // 构造 dialog
        val rootView = LayoutInflater.from(mContext)
                .inflate(R.layout.dialog_add_2_list, null, false)
        val dialog = Dialog(mContext)
        dialog.setCanceledOnTouchOutside(true)
        val layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog.addContentView(rootView, layoutParams)
        dialog.show()
        // 设置大小
        val attributes = dialog.window?.attributes
        attributes?.width = mContext.resources.displayMetrics.widthPixels
        attributes?.height = mContext.resources.displayMetrics.heightPixels / 2
        dialog.window?.attributes = attributes
        // 绑定数据
        val recyclerView = rootView.findViewById<RecyclerView>(R.id.mRcvMusicList)
        recyclerView.layoutManager = LinearLayoutManager(mContext)
        val adapter = MusicListAdapter(mContext, list)
        adapter.setItemClickListener(object : RecycleViewAdapter.OnItemClickListener {
            override fun onItemClick(holder: RViewHolder, position: Int) {
                MusicDataHelper.addMusic2List(music, list[position].listId,
                        object : MusicDataHelper.OnAddMusic2ListListener {
                            override fun addResult(code: Int, message: String) {
                                FToastUtils.init().show(message)
                            }
                        })
                dialog.dismiss()
            }

        })
        recyclerView.adapter = adapter
    }

    private fun lightOn() {
        try {
            val activity = mContext as Activity
            val attributes = activity.window?.attributes
            attributes?.alpha = 1.0f
            activity.window?.attributes = attributes
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun lightOff() {
        try {
            val activity = mContext as Activity
            val attributes = activity.window?.attributes
            attributes?.alpha = 0.6f
            activity.window?.attributes = attributes
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

}