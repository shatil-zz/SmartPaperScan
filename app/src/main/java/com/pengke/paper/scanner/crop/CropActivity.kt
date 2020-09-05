package com.pengke.paper.scanner.crop

import android.widget.ImageView
import com.pengke.paper.scanner.R
import com.pengke.paper.scanner.base.BaseActivity
import com.pengke.paper.scanner.view.PaperRectangle
import kotlinx.android.synthetic.main.activity_crop.*

class CropActivity : BaseActivity(), ICropView.Proxy {

    private lateinit var mPresenter: CropPresenter

    override fun prepare() {
        crop.setOnClickListener { mPresenter.crop() }
        enhance.setOnClickListener { mPresenter.enhance() }
        save.setOnClickListener { mPresenter.save() }
    }

    override fun provideContentViewId(): Int = R.layout.activity_crop


    override fun initPresenter() {
        mPresenter = CropPresenter(this, this)
    }

    override fun getPaper(): List<ImageView> {
        return listOf(paper, paper1, paper2, paper3, paper4,paper5)
    }

    override fun getPaperRect(): PaperRectangle = paper_rect

    override fun getCroppedPaper(): ImageView = picture_cropped
}