package com.zzr.bitmapdemo;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private List<String> urlList;
    private int spanCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        recyclerView = findViewById(R.id.recycler_view);
        spanCount = 2;
//        DividerItemDecoration divider = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        //瀑布流
        StaggeredGridLayoutManager manager = new StaggeredGridLayoutManager(spanCount, StaggeredGridLayoutManager.VERTICAL);
        //网格
        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount, RecyclerView.VERTICAL, false);
//        recyclerView.setLayoutManager(manager);
        recyclerView.setLayoutManager(layoutManager);

        recyclerView.setHasFixedSize(true);
        MyDecoration decoration = new MyDecoration(spanCount);
        GridSpacingItemDecoration itemDecoration = new GridSpacingItemDecoration(spanCount, 20, true);
//        recyclerView.addItemDecoration(itemDecoration);
        recyclerView.addItemDecoration(decoration);
        urlList = new ArrayList<>();
        String[] urls = Images.images;
        urlList = Arrays.asList(urls);
        MyAdapter myAdapter = new MyAdapter(this, urlList);
        recyclerView.setAdapter(myAdapter);
    }

    private class MyDecoration extends RecyclerView.ItemDecoration {
        int spanCount;

        public MyDecoration(int spanCount) {
            this.spanCount = spanCount;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
//            int right;
//            int itemPosition = ((RecyclerView.LayoutParams) view.getLayoutParams()).getViewLayoutPosition();
//            int space = DensityUtil.dip2px(parent.getContext(), 10);
//            if (itemPosition % 2 == 0) {
//                right = 0;
//            } else {
//                right = space;
//            }
            int space = DensityUtil.dip2px(parent.getContext(), 10);
            int position = parent.getChildAdapterPosition(view);
            //spanCount为2时，余数是0或者1
            int column = position % spanCount;

            //余数为0，表示所有位于左边那一列的itemView，它们的left是right的2倍，如left=10，right=5
            //余数为1，表示所有位于右边那一列的itemView，他们的right是left的2倍，如right=10，left=5
            //如此一来两列中间的间隙大小就和左边以及右边的一致了
//            outRect.left = space - column * space / spanCount;
//            outRect.right = (column + 1) * space / spanCount;
            //和上面的写法等同 更直白
//            if (column == 0) {
//                outRect.left = space;
//                outRect.right = space / 2;
//            } else {
//                outRect.left = space / 2;
//                outRect.right = space;
//            }

            //通用的，不管spanCount是多少（大于等于2）
            if (column == 0) {
                //最小的余数 最左边的一列
                outRect.left = space;
                outRect.right = space / 2;
            } else if (column == spanCount - 1) {
                //最大的余数 最右边的一列
                outRect.left = space / 2;
                outRect.right = space;
            } else {
                //中间的列
                outRect.left = space / 2;
                outRect.right = space / 2;
            }

            if (position < spanCount) {
                outRect.top = space;
            }
            outRect.bottom = space;
        }
    }
}
