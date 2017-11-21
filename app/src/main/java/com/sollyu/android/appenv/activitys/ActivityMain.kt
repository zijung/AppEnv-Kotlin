package com.sollyu.android.appenv.activitys

import android.annotation.SuppressLint
import android.app.Activity
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.design.widget.NavigationView
import android.support.v4.view.GravityCompat
import android.support.v4.widget.SwipeRefreshLayout
import android.support.v7.app.ActionBarDrawerToggle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.util.TypedValue
import android.view.*
import android.widget.Filter
import android.widget.Filterable
import com.elvishew.xlog.XLog
import com.sollyu.android.appenv.R
import com.sollyu.android.appenv.R.id.drawer_layout
import com.sollyu.android.appenv.R.id.swipeRefreshLayout
import com.sollyu.android.appenv.commons.Application
import com.sollyu.android.appenv.events.EventSample
import com.sollyu.android.libsuperuser.Shell
import com.sollyu.android.option.item.OptionItemView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.app_bar_activity_main.*
import kotlinx.android.synthetic.main.content_activity_main.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.xutils.view.annotation.ViewInject
import org.xutils.x
import java.util.*

class ActivityMain : ActivityBase(), NavigationView.OnNavigationItemSelectedListener, SwipeRefreshLayout.OnRefreshListener  {

    companion object {
        fun launch(activity: Activity) {
            activity.startActivity(Intent(activity, ActivityMain::class.java))
            activity.finish()
        }
    }

    private val recyclerViewAdapter = RecyclerViewAdapter()
    private val linearLayoutManager by lazy { LinearLayoutManager(activity) }

    override fun onInitView() {
        super.onInitView()

        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        x.view().inject(activity)
        EventBus.getDefault().register(this)

        val toggle = ActionBarDrawerToggle(this, drawer_layout, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close)
        drawer_layout.addDrawerListener(toggle)
        toggle.syncState()

        recyclerView.layoutManager = linearLayoutManager
        recyclerView.adapter = recyclerViewAdapter
        recyclerView.setItemViewCacheSize(1000)
    }

    override fun onInitListener() {
        super.onInitListener()
        nav_view.setNavigationItemSelectedListener(this)
        swipeRefreshLayout.setOnRefreshListener(this)
    }

    /**
     * 初始化结束
     */
    override fun onInitDone() {
        super.onInitDone()
        EventBus.getDefault().postSticky(EventSample(EventSample.TYPE.MAIN_REFRESH))
    }

    /**
     * 返回键被按下
     */
    override fun onBackPressed() {
        if (drawer_layout.isDrawerOpen(GravityCompat.START)) {
            drawer_layout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    /**
     * 本界面被销毁
     */
    override fun onDestroy() {
        super.onDestroy()
        EventBus.getDefault().unregister(this)
    }



    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.activity_main, menu)

        val searchView = menu.findItem(R.id.menu_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener{
            override fun onQueryTextSubmit(query: String?): Boolean {
                recyclerViewAdapter.filter.filter(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                recyclerViewAdapter.filter.filter(newText)
                return true
            }
        })
        return true
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        // Handle navigation view item clicks here.
        when (item.itemId) {
            R.id.nav_refresh -> {
                EventBus.getDefault().postSticky(EventSample(EventSample.TYPE.MAIN_REFRESH))
            }
            R.id.nav_settings -> {
                ActivitySettings.launch(activity)
            }
        }

        drawer_layout.closeDrawer(GravityCompat.START)
        return false
    }

    /**
     * 收到消息事件
     */
    @SuppressLint("SetTextI18n")
    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onRefresh(eventSample: EventSample) {
        when (eventSample.eventTYPE) {
            EventSample.TYPE.MAIN_REFRESH -> {
                swipeRefreshLayout.isRefreshing = false
                if (recyclerViewAdapter.installAppList.size == 0) {
                    val installPackage = Application.Instance.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

                    recyclerViewAdapter.installAppList.clear()
                    recyclerViewAdapter.installAppList.addAll(installPackage)
                }
                recyclerViewAdapter.filter.filter(null)
            }
        }
    }

    /**
     * 下拉刷新
     */
    override fun onRefresh() {
        EventBus.getDefault().postSticky(EventSample(EventSample.TYPE.MAIN_REFRESH))
    }


    /**
     * List Item
     */
    inner class RecyclerViewHolder(itemView: View?) : RecyclerView.ViewHolder(itemView), View.OnClickListener{

        @ViewInject(R.id.titleName)
        var tvTitleName: OptionItemView? = null

        init {
            x.view().inject(this, itemView)
            itemView?.setOnClickListener(this)
        }

        override fun onClick(v: View?) {
        }
    }

    inner class RecyclerViewAdapter : RecyclerView.Adapter<RecyclerViewHolder>(), Filterable {

        val displayAppList = LinkedList<ApplicationInfo>()
        val installAppList = LinkedList<ApplicationInfo>()

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RecyclerViewHolder {
            val view = LayoutInflater.from(activity).inflate(R.layout.item_listview, parent, false)
            val typedValue = TypedValue()
            activity.theme.resolveAttribute(R.attr.selectableItemBackground, typedValue, true)
            view.setBackgroundResource(typedValue.resourceId)
            return RecyclerViewHolder(view)
        }


        override fun onBindViewHolder(holder: RecyclerViewHolder?, position: Int) {
            val applicationInfo = displayAppList[position]
            val appLabel = applicationInfo.loadLabel(packageManager)
            val appPackageName = applicationInfo.packageName

            if (appLabel == appPackageName)
                holder?.tvTitleName?.setRightText(appPackageName)

            if (appLabel != appPackageName){
                holder?.tvTitleName?.setLeftText(appLabel)
                holder?.tvTitleName?.setRightText(appPackageName)
            }

        }

        override fun getItemCount(): Int {
            return displayAppList.size
        }

        override fun getFilter(): Filter {
            return object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults {
                    XLog.d(constraint)
                    val filterResults = FilterResults()
                    val displayAppListTmp = LinkedList<ApplicationInfo>()

                    if (constraint != null && constraint.isNotEmpty()) {
                        displayAppListTmp.addAll(installAppList.filter { it.packageName.toLowerCase().contains(constraint.toString().toLowerCase()) || it.loadLabel(packageManager).toString().toLowerCase().contains(constraint.toString().toLowerCase()) })
                    } else {
                        displayAppListTmp.addAll(installAppList)
                    }

                    filterResults.values = displayAppListTmp
                    filterResults.count  = displayAppListTmp.size
                    return filterResults
                }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    displayAppList.clear()
                    displayAppList.addAll(results?.values as LinkedList<ApplicationInfo>)
                    notifyDataSetChanged()
                }

            }
        }

    }
}