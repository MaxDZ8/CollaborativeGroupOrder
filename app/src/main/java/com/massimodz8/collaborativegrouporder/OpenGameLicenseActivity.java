package com.massimodz8.collaborativegrouporder;

import android.app.ActionBar;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;

public class OpenGameLicenseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_open_game_license);
        ActionBar ab = getActionBar();
        if(ab != null) ab.setDisplayHomeAsUpEnabled(true);
        final MyHandler handler = new MyHandler(this);
        handler.ticker = new Timer("incremental");
        handler.ticker.schedule(new TimerTask() {
            @Override
            public void run() { handler.sendEmptyMessage(MyHandler.TICK); }
        }, 0, 250);
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        super.onDestroy();
    }

    private volatile boolean destroyed;

    static class MyHandler extends Handler {
        static final int TICK = 1;
        final WeakReference<OpenGameLicenseActivity> target;
        int next;
        static final int[] show = {
                R.id.ogla_1_definitions, R.id.ogla_1_body_a, R.id.ogla_1_body_b, R.id.ogla_1_body_c, R.id.ogla_1_body_d, R.id.ogla_1_body_e, R.id.ogla_1_body_f, R.id.ogla_1_body_g, R.id.ogla_1_body_h,
                R.id.ogla_2_the_license, R.id.ogla_2_body,
                R.id.ogla_3_offer_and_acceptance, R.id.ogla_3_body,
                R.id.ogla_4_grant_and_consideration, R.id.ogla_4_body,
                R.id.ogla_5_representation_of_autorithy_to_contribute, R.id.ogla_5_body,
                R.id.ogla_6_notice_of_license_copyright, R.id.ogla_6_body,
                R.id.ogla_7_use_of_product_identity, R.id.ogla_7_body,
                R.id.ogla_8_identification, R.id.ogla_8_body,
                R.id.ogla_9_updating_the_license, R.id.ogla_9_body,
                R.id.ogla_10_copy_of_this_license, R.id.ogla_10_body,
                R.id.ogla_11_use_of_contributor_credits, R.id.ogla_11_body,
                R.id.ogla_12_inability_to_comply, R.id.ogla_12_body,
                R.id.ogla_13_termination, R.id.ogla_13_body,
                R.id.ogla_14_reformation, R.id.ogla_14_body,
                R.id.ogla_15_copyright_notice,
                R.id.ogla_15_a1, R.id.ogla_15_a2,
                R.id.ogla_15_b1, R.id.ogla_15_b2,
                R.id.ogla_15_c1, R.id.ogla_15_c2,
                R.id.ogla_15_d1, R.id.ogla_15_d2, R.id.ogla_15_d3,
                R.id.ogla_15_e, R.id.ogla_15_f, R.id.ogla_15_g, R.id.ogla_15_h,
                R.id.ogla_15_i, R.id.ogla_15_j,
                R.id.ogla_15_k1, R.id.ogla_15_k2,
                R.id.ogla_15_l,
                R.id.ogla_15_m1, R.id.ogla_15_m2,
                R.id.ogla_15_n, R.id.ogla_15_o,
                R.id.ogla_15_p1, R.id.ogla_15_p2, R.id.ogla_15_p3, R.id.ogla_15_p4, R.id.ogla_15_p5, R.id.ogla_15_p6, R.id.ogla_15_p7, R.id.ogla_15_p8,R.id.ogla_15_p9,
                R.id.ogla_15_q, R.id.ogla_15_r,
                R.id.ogla_15_s1, R.id.ogla_15_s2,
                R.id.ogla_15_t1, R.id.ogla_15_t2, R.id.ogla_15_t3, R.id.ogla_15_t4, R.id.ogla_15_t5, R.id.ogla_15_t6,
                R.id.ogla_15_u, R.id.ogla_15_v,
                R.id.ogla_15_w1, R.id.ogla_15_w2,
                //R.id.ogla_15_x,
                R.id.ogla_15_y, R.id.ogla_15_z
        };
        public Timer ticker;


        MyHandler(OpenGameLicenseActivity target) {
            this.target = new WeakReference<>(target);
        }

        @Override
        public void handleMessage(Message msg) {
            if(msg.what != TICK) return; // impossible
            final OpenGameLicenseActivity self = target.get();
            if(self.destroyed) {
                ticker.cancel();
                return;
            }
            if(next == show.length) return;
            if(next < show.length / 4) MaxUtils.beginDelayedTransition(self);
            self.findViewById(show[next++]).setVisibility(View.VISIBLE);
            if(next == show.length) ticker.cancel();
        }
    }
}
