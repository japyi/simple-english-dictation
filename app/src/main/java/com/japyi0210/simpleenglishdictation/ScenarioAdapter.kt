package com.japyi0210.simpleenglishdictation

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class ScenarioAdapter(
    private val context: Context,
    private val scenarios: List<ScenarioSelectActivity.Scenario>
) : BaseAdapter() {

    override fun getCount(): Int = scenarios.size
    override fun getItem(position: Int): Any = scenarios[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_scenario, parent, false)

        val scenario = scenarios[position]

        val textView = view.findViewById<TextView>(R.id.textScenarioName)
        val imageView = view.findViewById<ImageView>(R.id.imageBackground)

        textView.text = scenario.name

        try {
            val ims = context.assets.open("scenarios/${scenario.fileKey}.webp")
            val drawable = Drawable.createFromStream(ims, null)
            imageView.setImageDrawable(drawable)
        } catch (e: Exception) {
            imageView.setImageResource(R.drawable.default_background) // 예비 이미지
        }

        return view
    }
}
