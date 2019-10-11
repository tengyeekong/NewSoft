package com.solution.it.newsoft.ui;

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import dagger.android.support.DaggerAppCompatActivity;
import io.reactivex.Completable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

import com.github.razir.progressbutton.ButtonTextAnimatorExtensionsKt;
import com.github.razir.progressbutton.DrawableButtonExtensionsKt;
import com.github.razir.progressbutton.ProgressButtonHolderKt;
import com.github.razir.progressbutton.ProgressParams;
import com.github.razir.progressbutton.TextChangeAnimatorParams;
import com.google.android.material.snackbar.Snackbar;
import com.solution.it.newsoft.R;
import com.solution.it.newsoft.databinding.ActivityListingBinding;
import com.solution.it.newsoft.databinding.DialogUpdateListBinding;
import com.solution.it.newsoft.model.List;
import com.solution.it.newsoft.paging.ListingAdapter;
import com.solution.it.newsoft.viewmodel.ListingViewModel;

import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

public class ListingActivity extends DaggerAppCompatActivity {
    private ActivityListingBinding binding;
    private ListingViewModel listingViewModel;
    private LinearLayoutManager layoutManager;
    private CompositeDisposable disposable = new CompositeDisposable();
//    private Toast toast;
    private Snackbar snackbar;

    @Inject
    ViewModelProvider.Factory viewModelFactory;

    @Inject
    ListingAdapter adapter;

    @Inject
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_listing);

        getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        getSupportActionBar().setDisplayShowHomeEnabled(false);
        getSupportActionBar().setTitle(prefs.getString(ListingViewModel.USERNAME, ""));

        layoutManager = new LinearLayoutManager(this);
        binding.recyclerView.setLayoutManager(layoutManager);
        binding.recyclerView.setHasFixedSize(true);

        binding.recyclerView.setAdapter(adapter);

        listingViewModel = ViewModelProviders.of(this, viewModelFactory).get(ListingViewModel.class);
        binding.swipeRefresh.setRefreshing(true);
        listingViewModel.getListLiveData().observe(this, lists -> adapter.submitList(lists));
        listingViewModel.getNetworkState().observe(this, networkState -> {
            adapter.setNetworkState(networkState);
            if (binding.swipeRefresh.isRefreshing())
                binding.swipeRefresh.setRefreshing(false);
        });

        binding.swipeRefresh.setColorSchemeColors(getColor(R.color.colorPrimary));
        binding.swipeRefresh.setOnRefreshListener(() -> refreshList());

        adapter.setOnItemClickListener(new ListingAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(List list) {
                snackbar = Snackbar.make(binding.coordinatorLayout,
                        new StringBuilder("List name: ").append(list.getList_name())
                        .append("\n")
                        .append("Distance: ").append(list.getDistance()), Snackbar.LENGTH_SHORT);
                snackbar.show();

//                if (toast != null) toast.cancel();
//                toast = Toast.makeText(ListingActivity.this,
//                        new StringBuilder("List name: ").append(list.getList_name())
//                                .append("\n")
//                                .append("Distance: ").append(list.getDistance()), Toast.LENGTH_SHORT);
//                toast.show();
            }

            @Override
            public void onItemLongClick(List list, int position) {
                showUpdateDialog(list, position);
            }

            @Override
            public void onRetryClick() {
                listingViewModel.retry();
            }
        });
    }

    private void refreshList() {
        binding.swipeRefresh.setRefreshing(true);
        listingViewModel.reload();
    }

    private void showUpdateDialog(List list, int position) {
        Dialog dialog = new Dialog(ListingActivity.this);
        DialogUpdateListBinding dialogBinding = DialogUpdateListBinding.inflate(LayoutInflater.from(ListingActivity.this), (ViewGroup) binding.getRoot(), false);
        dialogBinding.setList(list);
        dialogBinding.etListName.requestFocus();
        dialogBinding.btnUpdate.setOnClickListener(view -> updateList(dialogBinding, list, position, dialog));

        dialog.setContentView(dialogBinding.getRoot());
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
    }

    private void updateList(DialogUpdateListBinding dialogBinding, List list, int position, Dialog dialog) {
        ProgressButtonHolderKt.bindProgressButton(this, dialogBinding.btnUpdate);
        ButtonTextAnimatorExtensionsKt.attachTextChangeAnimator(dialogBinding.btnUpdate, new TextChangeAnimatorParams());
        ProgressParams params = new ProgressParams();
        params.setButtonText("Updating");
        params.setProgressColorRes(R.color.colorPrimary);
        DrawableButtonExtensionsKt.showProgress(dialogBinding.btnUpdate, params);

        LiveData<Boolean> isUpdated = listingViewModel.updateList(list.getId(), dialogBinding.etListName.getText().toString(),
                dialogBinding.etDistance.getText().toString());
        isUpdated.observe(ListingActivity.this, aBoolean -> {
            if (aBoolean) {
                adapter.updateList(position, dialogBinding.etListName.getText().toString(),
                        dialogBinding.etDistance.getText().toString());
            }
            DrawableButtonExtensionsKt.hideProgress(dialogBinding.btnUpdate, "Updated");
            final Handler handler = new Handler();
            handler.postDelayed(dialog::dismiss, 1_000);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.logout) {
            prefs.edit().clear().apply();
            Intent intent = new Intent(ListingActivity.this, LoginActivity.class);
            startActivity(intent);
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (layoutManager.findFirstVisibleItemPosition() > 5) {
            disposable.add(Completable.fromAction(() ->
                    binding.recyclerView.smoothScrollToPosition(0))
                    .delay(200, TimeUnit.MILLISECONDS)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(() -> layoutManager.scrollToPositionWithOffset(0, 0)));
        } else {
            super.onBackPressed();
            finishAffinity();
        }
    }

    @Override
    protected void onDestroy() {
        disposable.clear();
        super.onDestroy();
    }
}
