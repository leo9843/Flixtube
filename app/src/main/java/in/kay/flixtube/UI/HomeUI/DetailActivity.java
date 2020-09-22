package in.kay.flixtube.UI.HomeUI;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.blogspot.atifsoftwares.animatoolib.Animatoo;
import com.firebase.ui.database.FirebaseRecyclerOptions;
import com.gdacciaro.iOSDialog.iOSDialog;
import com.gdacciaro.iOSDialog.iOSDialogBuilder;
import com.gdacciaro.iOSDialog.iOSDialogClickListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.razorpay.Checkout;
import com.razorpay.PaymentResultListener;
import com.sdsmdg.tastytoast.TastyToast;
import com.squareup.picasso.Picasso;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import in.kay.flixtube.Adapter.SeriesPlayAdapter;
import in.kay.flixtube.Model.SeriesModel;
import in.kay.flixtube.R;
import in.kay.flixtube.Utils.Helper;

public class DetailActivity extends AppCompatActivity implements PaymentResultListener {

    String imdb, trailer, url, type, title, image, contentType;
    TextView tvTitle, tvTime, tvPlot, tvCasting, tvGenre, tvAbout, tvAward, tvAwards, tvCastName, tvImdb, tvSeasons, tvWatch;
    ImageView iv;
    Helper helper;
    RecyclerView rvSeries;
    DatabaseReference rootRef;
    SeriesPlayAdapter seriesPlayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);
        helper = new Helper();
        rootRef = FirebaseDatabase.getInstance().getReference();
        CheckInternet();

    }

    private void CheckInternet() {
        if (helper.isNetwork(this)) {
            InitAll();
        } else {
            Typeface font = Typeface.createFromAsset(this.getAssets(), "Gilroy-ExtraBold.ttf");
            new iOSDialogBuilder(this)
                    .setTitle("Oh shucks!")
                    .setSubtitle("Slow or no internet connection.\nPlease check your internet settings")
                    .setCancelable(false)
                    .setFont(font)
                    .setPositiveListener(getString(R.string.ok), new iOSDialogClickListener() {
                        @Override
                        public void onClick(iOSDialog dialog) {
                            CheckInternet();
                            dialog.dismiss();
                        }
                    })
                    .build().show();
        }
    }

    private void InitAll() {
        rootRef.child("User").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                final String membership = snapshot.child("Membership").getValue(String.class);
                Initz(membership);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void Initz(final String membership) {
        GetValues();
        InitzViews();
        GetData getData = new GetData();
        getData.execute();
        findViewById(R.id.btn_play).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PlayMovie(membership);
            }
        });
        findViewById(R.id.btn_download).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Download();
            }
        });
    }

    private void LoadSeries(JSONObject jsonObject) {
        if (type.equalsIgnoreCase("Series") || type.equalsIgnoreCase("Webseries")) {
            String movieSeason = null;
            try {
                movieSeason = jsonObject.getString("totalSeasons");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            tvSeasons.setText("Total Seasons " + movieSeason);
            tvSeasons.setVisibility(View.VISIBLE);
            findViewById(R.id.ll).setVisibility(View.GONE);
            rvSeries.setVisibility(View.VISIBLE);
            findViewById(R.id.tv_watch).setVisibility(View.VISIBLE);
            findViewById(R.id.ll).setVisibility(View.GONE);
            String key = getIntent().getStringExtra("key");
            FirebaseRecyclerOptions<SeriesModel> options = new FirebaseRecyclerOptions.Builder<SeriesModel>()
                    .setQuery(rootRef.child("Webseries").child(key).child("Source"), SeriesModel.class)
                    .build();
            seriesPlayAdapter = new SeriesPlayAdapter(options, this, image);
            rvSeries.setAdapter(seriesPlayAdapter);
            seriesPlayAdapter.startListening();
        }
    }

    @Override
    public void onPaymentSuccess(String s) {
        rootRef.child("User").child(FirebaseAuth.getInstance().getCurrentUser().getUid()).child("Membership").setValue("VIP").addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                TastyToast.makeText(DetailActivity.this, "Welcome to Flixtube VIP club...", TastyToast.LENGTH_LONG, TastyToast.SUCCESS);
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                TastyToast.makeText(DetailActivity.this, "Server down. Error : " + e, TastyToast.LENGTH_LONG, TastyToast.ERROR);
            }
        });
    }

    @Override
    public void onPaymentError(int i, String s) {
        TastyToast.makeText(this, "Payment cancelled.", TastyToast.LENGTH_LONG, TastyToast.ERROR);
    }

    private class GetData extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            findViewById(R.id.progressBar).setVisibility(View.GONE);
            findViewById(R.id.nsv_detail).setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                GetDatafromURL();
            } catch (JSONException | IOException e) {
                TastyToast.makeText(DetailActivity.this, "Something went wrong.", TastyToast.LENGTH_LONG, TastyToast.ERROR);
            }
            return null;
        }
    }

    private void GetDatafromURL() throws IOException, JSONException {
        String strUrl = "http://www.omdbapi.com/?apikey=a7008f3&i=" + imdb + "&plot=long";
        URL url = new URL(strUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        InputStream inputStream = connection.getInputStream();
        if (inputStream == null) {
            TastyToast.makeText(DetailActivity.this, "Something went wrong.", TastyToast.LENGTH_LONG, TastyToast.ERROR);
        }
        BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
        String line;
        StringBuilder stringBuilder = new StringBuilder();
        while ((line = br.readLine()) != null) {
            stringBuilder.append(line);
        }
        final JSONObject jsonObject = new JSONObject(stringBuilder.toString());
        final String movieName = title = jsonObject.getString("Title");
        final String movieGenre = jsonObject.getString("Genre");
        final String movieImdb = jsonObject.getString("imdbRating");
        final String movieDate = jsonObject.getString("Released");
        final String movieTime = jsonObject.getString("Runtime");
        final String moviePoster = image = jsonObject.getString("Poster");
        final String moviePlot = jsonObject.getString("Plot");
        final String movieCast = jsonObject.getString("Actors");
        final String movieAward = jsonObject.getString("Awards");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                UpdateUI(jsonObject, movieName, movieGenre, movieImdb, movieDate, movieTime, moviePoster, moviePlot, movieCast, movieAward);
            }
        });

    }

    private void UpdateUI(JSONObject jsonObject, String movieName, String movieGenre, String movieImdb, String movieDate, String movieTime, String moviePoster, String moviePlot, String movieCast, String movieAward) {
        tvTitle.setText(movieName);
        tvAbout.setText(moviePlot);
        tvGenre.setText(movieGenre + "  |  " + movieDate);
        tvImdb.setText(movieImdb + "/10");
        tvCastName.setText(movieCast);
        tvAwards.setText(movieAward);
        tvTime.setText(movieTime);
        Picasso.get()
                .load(moviePoster)
                .into(iv);
        LoadSeries(jsonObject);
    }

    private void GetValues() {
        imdb = getIntent().getStringExtra("imdb");
        trailer = getIntent().getStringExtra("trailer");
        type = getIntent().getStringExtra("type");
        url = getIntent().getStringExtra("url");
        contentType = getIntent().getStringExtra("movieType");
    }

    private void InitzViews() {
        Typeface font = Typeface.createFromAsset(this.getAssets(), "Gilroy-ExtraBold.ttf");
        Typeface brandon = Typeface.createFromAsset(this.getAssets(), "Brandon.ttf");
        Typeface typeface = Typeface.createFromAsset(this.getAssets(), "Gilroy-Light.ttf");
        /////////////////////////////////
        tvTitle = findViewById(R.id.tv_title);
        tvCasting = findViewById(R.id.tv_casting);
        tvImdb = findViewById(R.id.tv_imdb);
        tvCastName = findViewById(R.id.tv_cast_name);
        tvPlot = findViewById(R.id.tv_plot);
        tvTime = findViewById(R.id.tv_time);
        tvGenre = findViewById(R.id.tv_genre);
        tvWatch = findViewById(R.id.tv_watch);
        tvAbout = findViewById(R.id.tv_about);
        tvSeasons = findViewById(R.id.tv_seasons);
        tvAward = findViewById(R.id.tv_award);
        tvAwards = findViewById(R.id.tv_awards);
        /////////////////////////////////
        iv = findViewById(R.id.iv_cover_img);
        /////////////////////////////////
        rvSeries = findViewById(R.id.rv_series);
        rvSeries.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        /////////////////////////////////
        tvAbout.setTypeface(typeface);
        tvGenre.setTypeface(typeface);
        tvCastName.setTypeface(typeface);
        tvAwards.setTypeface(typeface);
        tvImdb.setTypeface(typeface);
        tvTitle.setTypeface(font);
        tvPlot.setTypeface(brandon);
        tvCasting.setTypeface(brandon);
        tvAward.setTypeface(brandon);
        tvWatch.setTypeface(brandon);

    }

    public void PlayMovie(String membership) {
        if (contentType.equalsIgnoreCase("Premium")) {
            if (membership.equalsIgnoreCase("VIP")) {
                Intent intent = new Intent(this, PlayerActivity.class);
                intent.putExtra("url", url);
                intent.putExtra("title", title);
                startActivity(intent);
            } else {
                ShowPopup();
            }
        } else {
            Intent intent = new Intent(this, PlayerActivity.class);
            intent.putExtra("url", url);
            intent.putExtra("title", title);
            startActivity(intent);
        }
    }

    private void ShowPopup() {
        Typeface font = Typeface.createFromAsset(this.getAssets(), "Gilroy-ExtraBold.ttf");
        new iOSDialogBuilder(DetailActivity.this)
                .setTitle("Buy Premium")
                .setSubtitle("You discovered a premium feature. Streaming a premium content requires VIP account. Press buy to continue")
                .setBoldPositiveLabel(true)
                .setFont(font)
                .setCancelable(false)
                .setPositiveListener(getString(R.string.buy), new iOSDialogClickListener() {
                    @Override
                    public void onClick(iOSDialog dialog) {
                        BuyAccount();
                        dialog.dismiss();

                    }
                })
                .setNegativeListener(getString(R.string.dismiss), new iOSDialogClickListener() {
                    @Override
                    public void onClick(iOSDialog dialog) {
                        dialog.dismiss();
                    }
                })
                .build().show();
    }

    private void BuyAccount() {
        Checkout checkout = new Checkout();
        checkout.setKeyID("rzp_test_sKxf90ARlhoVdi");
        final Activity activity = this;
        try {
            JSONObject options = new JSONObject();
            options.put("name", "Flixtube");
            options.put("description", "Purchase premium Flixtube account");
            options.put("currency", "INR");
            String paisee = Integer.toString(Integer.parseInt("200") * 100);
            options.put("amount", paisee);
            checkout.open(activity, options);
        } catch (Exception e) {
            Toast.makeText(this, "Payment error please try again" + e, Toast.LENGTH_SHORT).show();
        }
    }

    public void Download() {
        TastyToast.makeText(this, "Downloading " + title, TastyToast.LENGTH_LONG, TastyToast.SUCCESS);
        helper.DownloadFile(this, title, "Movie", url);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        Animatoo.animateSlideRight(this);
    }

    public void TrailerPlay(View view) {
        Intent intent = new Intent(this, PlayerActivity.class);
        intent.putExtra("url", trailer);
        intent.putExtra("title", title + " trailer");
        startActivity(intent);
    }
}