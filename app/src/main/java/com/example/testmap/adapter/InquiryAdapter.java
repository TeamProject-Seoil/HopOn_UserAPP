package com.example.testmap.adapter;

import android.app.Dialog;
import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewPropertyAnimator;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.example.testmap.R;
import com.example.testmap.service.ApiClient;
import com.example.testmap.service.ApiService;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class InquiryAdapter extends RecyclerView.Adapter<InquiryAdapter.VH> {

    private final List<ApiService.InquiryResp> items = new ArrayList<>();
    private final Set<Long> expandedIds = new HashSet<>();
    /** 성공적으로 비밀번호 검증을 마친 항목 추적(클라이언트 로컬 상태) */
    private final Set<Long> unlockedIds = new HashSet<>();

    /** true=공개 목록(비밀글 잠금), false=내 목록 */
    private boolean publicMode = true;

    // ====== 카운트 콜백 인터페이스 & 리스너 ======
    public static class Counts {
        public final long all, open, answered, closed;
        public Counts(long all, long open, long answered, long closed) {
            this.all = all; this.open = open; this.answered = answered; this.closed = closed;
        }
    }
    public interface OnCountsChangeListener { void onCountsChanged(Counts c); }
    private OnCountsChangeListener countsListener;

    public void setOnCountsChangeListener(OnCountsChangeListener l) { this.countsListener = l; }

    public InquiryAdapter() { setHasStableIds(true); }

    public void setPublicMode(boolean on) { this.publicMode = on; notifyDataSetChanged(); recalcAndEmitCounts(); }

    public void setData(List<ApiService.InquiryResp> data, boolean clear) {
        if (clear) { items.clear(); expandedIds.clear(); unlockedIds.clear(); }
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
        recalcAndEmitCounts();
    }

    /** 서버에서 단일 항목 최신 상태를 받았을 때 적용 (id 기준 교체) */
    public void applyServerUpdate(ApiService.InquiryResp updated) {
        if (updated == null || updated.id == null) return;
        for (int i = 0; i < items.size(); i++) {
            ApiService.InquiryResp cur = items.get(i);
            if (cur != null && updated.id.equals(cur.id)) {
                items.set(i, updated);
                notifyItemChanged(i);
                recalcAndEmitCounts();
                return;
            }
        }
    }

    /** 로컬에서 상태만 바꿔야 할 때 간편 메서드 */
    public void setStatus(Long id, String newStatus) {
        if (id == null || newStatus == null) return;
        for (int i = 0; i < items.size(); i++) {
            ApiService.InquiryResp cur = items.get(i);
            if (cur != null && id.equals(cur.id)) {
                cur.status = newStatus;
                notifyItemChanged(i);
                recalcAndEmitCounts();
                return;
            }
        }
    }

    private void recalcAndEmitCounts() {
        long all = items.size();
        long open = 0, answered = 0, closed = 0;
        for (ApiService.InquiryResp x : items) {
            if (x == null || x.status == null) continue;
            switch (x.status) {
                case "OPEN" -> open++;
                case "ANSWERED" -> answered++;
                case "CLOSED" -> closed++;
            }
        }
        if (countsListener != null) countsListener.onCountsChanged(new Counts(all, open, answered, closed));
    }

    @Override public long getItemId(int position) {
        ApiService.InquiryResp x = items.get(position);
        return (x != null && x.id != null) ? x.id : RecyclerView.NO_ID;
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inquiry_card, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int pos) {
        ApiService.InquiryResp it = items.get(pos);
        boolean open = it.id != null && expandedIds.contains(it.id);
        boolean unlocked = it.id != null && unlockedIds.contains(it.id);

        cancel(h.expandArea);

        h.author.setText(maskName(it.name));
        h.title.setText(s(it.title));
        h.badge.setText(mapStatus(it.status));
        h.date.setText(fmtDate(it.createdAt));

        int att = it.attachments == null ? 0 : it.attachments.size();
        int rep = it.replies == null ? 0 : it.replies.size();
        h.meta.setText("첨부 " + att + "개 · 답변 " + rep + "개");

        @ColorInt int selected = 0xFFE8F0FE;
        @ColorInt int white = 0xFFFFFFFF;

        boolean isSecret = it.secret;
        // 🔒 잠금 아이콘: 공개 모드 + 비밀글 + 아직 해제 안 됨일 때만 표시
        h.lock.setVisibility(publicMode && isSecret && !unlocked ? View.VISIBLE : View.GONE);

        if (publicMode && isSecret && !unlocked) {
            // 아직 잠겨있는 상태: 내용 감추고, 클릭 시 비번 모달만
            h.content.setText("🔒 비밀글입니다");
            h.container.setBackgroundColor(white);
            h.expandArea.setVisibility(View.GONE);
            h.arrow.setRotation(0f);
            h.header.setOnClickListener(v -> {
                int adapterPos = h.getBindingAdapterPosition();
                if (adapterPos == RecyclerView.NO_POSITION) return;
                showPasswordDialog(h.itemView.getContext(), it, adapterPos);
            });
        } else {
            // 공개 글 또는 이미 해제된 비밀글(로컬 unlocked)
            h.content.setText(s(it.content));
            h.container.setBackgroundColor(open ? selected : white);
            h.expandArea.setVisibility(open ? View.VISIBLE : View.GONE);
            h.arrow.setRotation(open ? 180f : 0f);
            h.header.setOnClickListener(v -> toggleExpand(h, it));
        }
    }

    /** 🔐 머티리얼 모달: 비밀번호 검증 성공 시에만 unlockedIds에 기록 후 펼침 */
    private void showPasswordDialog(Context ctx, ApiService.InquiryResp item, int position) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.dialog_inquiry_password, null, false);
        TextInputLayout til = view.findViewById(R.id.til_password);
        TextInputEditText et = view.findViewById(R.id.et_password);
        ProgressBar progress = view.findViewById(R.id.progress);

        til.setEndIconMode(TextInputLayout.END_ICON_PASSWORD_TOGGLE);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        AlertDialog dialog = new MaterialAlertDialogBuilder(ctx)
                .setTitle("비밀글 확인")
                .setView(view)
                .setPositiveButton("확인", null) // onShow에서 수동 제어
                .setNegativeButton("취소", (d, w) -> d.dismiss())
                .create();

        dialog.setOnShowListener(dlg -> {
            final TextView btnOk = dialog.getButton(Dialog.BUTTON_POSITIVE);
            final TextView btnCancel = dialog.getButton(Dialog.BUTTON_NEGATIVE);

            btnOk.setOnClickListener(v -> {
                String pwd = et.getText() == null ? "" : et.getText().toString().trim();
                til.setError(null);

                if (pwd.isEmpty()) {
                    til.setError("비밀번호를 입력하세요");
                    return;
                }

                setDialogLoading(true, btnOk, btnCancel, progress);

                ApiClient.get().getInquiryPublicDetail(item.id, pwd)
                        .enqueue(new Callback<ApiService.InquiryResp>() {
                            @Override
                            public void onResponse(Call<ApiService.InquiryResp> call, Response<ApiService.InquiryResp> res) {
                                setDialogLoading(false, btnOk, btnCancel, progress);

                                if (!res.isSuccessful() || res.body() == null) {
                                    til.setError("열람 실패 (" + res.code() + ")");
                                    return;
                                }

                                ApiService.InquiryResp body = res.body();

                                // ✅ 서버가 잠금 유지 응답(마스킹)일 수 있으므로 content 유무/길이로 판단
                                boolean contentPresent = !TextUtils.isEmpty(s(body.content));
                                if (!contentPresent) {
                                    til.setError("비밀번호가 올바르지 않습니다");
                                    return;
                                }

                                // 성공: 로컬 상태로 해제 처리(unlockedIds)
                                if (item.id != null) {
                                    unlockedIds.add(item.id);
                                    expandedIds.add(item.id); // 자동 펼침
                                }

                                // 최신 내용 반영
                                item.content = body.content;
                                item.attachments = body.attachments;
                                item.replies = body.replies;

                                notifyItemChanged(position);
                                Toast.makeText(ctx, "비밀글 열람 성공", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            }

                            @Override
                            public void onFailure(Call<ApiService.InquiryResp> call, Throwable t) {
                                setDialogLoading(false, btnOk, btnCancel, progress);
                                til.setError("서버 오류: " + t.getMessage());
                            }
                        });
            });
        });

        dialog.show();
    }

    private void setDialogLoading(boolean loading, TextView ok, TextView cancel, ProgressBar progress) {
        ok.setEnabled(!loading);
        cancel.setEnabled(!loading);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void toggleExpand(@NonNull VH h, ApiService.InquiryResp cur) {
        if (cur == null || cur.id == null) return;

        // 🔒 안전장치: 공개 모드에서 아직 잠긴 글은 확장 금지
        if (publicMode && cur.secret && !unlockedIds.contains(cur.id)) return;

        cancel(h.expandArea);
        boolean now = !expandedIds.contains(cur.id);
        if (now) expandedIds.add(cur.id); else expandedIds.remove(cur.id);

        @ColorInt int selected = 0xFFE8F0FE; @ColorInt int white = 0xFFFFFFFF;
        h.container.setBackgroundColor(now ? selected : white);
        h.arrow.animate().rotation(now ? 180f : 0f).setDuration(160).start();

        if (now) {
            h.expandArea.setAlpha(0f);
            h.expandArea.setVisibility(View.VISIBLE);
            h.expandArea.animate().alpha(1f).setDuration(160).start();
        } else {
            h.expandArea.animate().alpha(0f).setDuration(140)
                    .withEndAction(() -> { h.expandArea.setVisibility(View.GONE); h.expandArea.setAlpha(1f); })
                    .start();
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        CardView card;
        View container, header, expandArea;
        TextView badge, title, date, content, meta, author;
        ImageView arrow, lock;
        VH(@NonNull View v) {
            super(v);
            card = (CardView) v;
            container = v.findViewById(R.id.container);
            header = v.findViewById(R.id.header);
            expandArea = v.findViewById(R.id.expand_area);
            badge = v.findViewById(R.id.badge);
            title = v.findViewById(R.id.title);
            date = v.findViewById(R.id.date);
            content = v.findViewById(R.id.content);
            meta = v.findViewById(R.id.meta);
            author = v.findViewById(R.id.author);
            arrow = v.findViewById(R.id.arrow);
            lock = v.findViewById(R.id.ic_lock);
        }
    }

    private static void cancel(View v){ if (v==null)return; ViewPropertyAnimator a=v.animate(); if(a!=null)a.cancel(); v.clearAnimation(); }
    private static String s(String x){ return x==null? "": x; }
    private static String mapStatus(String st){ if(st==null)return"접수"; return switch(st){ case"OPEN"->"접수"; case"ANSWERED"->"답변"; case"CLOSED"->"종료"; default->"접수"; }; }
    private static String fmtDate(String iso){ if(TextUtils.isEmpty(iso)||iso.length()<10)return""; return iso.substring(0,10).replace("-","."); }
    private static String maskName(String n){ if(TextUtils.isEmpty(n))return"익명"; return n.length()>1?n.substring(0,1)+"＊＊":n; }
}
