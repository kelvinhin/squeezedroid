package net.chrislehmann.squeezedroid.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ExpandableListView.ExpandableListContextMenuInfo;
import android.widget.ExpandableListView.OnChildClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import net.chrislehmann.squeezedroid.R;
import net.chrislehmann.squeezedroid.listadapter.SearchResultExpandableListAdapter;
import net.chrislehmann.squeezedroid.model.Album;
import net.chrislehmann.squeezedroid.model.Artist;
import net.chrislehmann.squeezedroid.model.Genre;
import net.chrislehmann.squeezedroid.model.Item;
import net.chrislehmann.squeezedroid.model.SearchResult;
import net.chrislehmann.squeezedroid.service.ServiceConnectionManager.SqueezeServiceAwareThread;
import net.chrislehmann.squeezedroid.service.SqueezeService;
import net.chrislehmann.squeezedroid.view.NowPlayingInfoPanel;
import org.apache.commons.lang3.StringUtils;

public class SearchActivity extends SqueezedroidActivitySupport {

    private static final int MENU_DONE = 838;
    private static final int CONTEXTMENU_ADD_ITEM = 121;
    private static final int CONTEXTMENU_PLAY_ITEM = 122;
    protected static final int CONTEXTMENU_PLAY_NEXT = 123;

    protected ExpandableListView resultsExpandableListView;
    protected EditText searchCriteriaText;
    protected ImageButton searchButton;
    protected TextView searchInstructionsText;
    protected TextView noResultsFoundText;

    protected SearchResultExpandableListAdapter adapter = new SearchResultExpandableListAdapter();
    private Context context = this;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.search_layout);

        searchButton = (ImageButton) findViewById(R.id.search_button);
        searchCriteriaText = (EditText) findViewById(R.id.search_input);
        searchInstructionsText = (TextView) findViewById(R.id.search_instructions_text);
        noResultsFoundText = (TextView) findViewById(R.id.search_no_results_text);

        resultsExpandableListView = (ExpandableListView) findViewById(R.id.search_expandable_list);
        resultsExpandableListView.setAdapter(adapter);
        resultsExpandableListView.setOnCreateContextMenuListener(new OnCreateContextMenuListener() {

            public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
                ExpandableListContextMenuInfo contextMenuInfo = (ExpandableListContextMenuInfo) menuInfo;
                int child = ExpandableListView.getPackedPositionChild(contextMenuInfo.packedPosition);
                if (child >= 0) {
                    menu.add(Menu.NONE, CONTEXTMENU_ADD_ITEM, 0, "Add To Playlist");
                    menu.add(Menu.NONE, CONTEXTMENU_PLAY_ITEM, 1, "Play Now");
                    menu.add(Menu.NONE, CONTEXTMENU_PLAY_NEXT, 1, "Play Next");
                }
            }
        });
        searchButton.setOnClickListener(onSearchButtonClicked);

        resultsExpandableListView.setOnChildClickListener(onChildClicked);

        NowPlayingInfoPanel nowPlayingInfoPanel = (NowPlayingInfoPanel) findViewById(R.id.song_info_container);
        if (nowPlayingInfoPanel != null) {
            nowPlayingInfoPanel.setParent(this);
        }

    }

    ;

    OnChildClickListener onChildClicked = new OnChildClickListener() {
        public boolean onChildClick(ExpandableListView parent, View v, int groupPosition, int childPosition, long id) {
            boolean handled = false;
            Item item = (Item) adapter.getChild(groupPosition, childPosition);
            if (item != null) {
                Intent i = new Intent();
                if (item instanceof Album) {
                    i.setAction("net.chrislehmann.squeezedroid.action.BrowseSong");
                    i.setData(Uri.parse("squeeze:///album/" + item.getId()));
                    startActivityForResult(i, SqueezeDroidConstants.RequestCodes.REQUEST_BROWSE);
                    handled = true;
                }
                if (item instanceof Genre) {
                    i.setAction("net.chrislehmann.squeezedroid.action.BrowseArtist");
                    i.setData(Uri.parse("squeeze:///genre/" + item.getId()));
                    startActivityForResult(i, SqueezeDroidConstants.RequestCodes.REQUEST_BROWSE);
                    handled = true;
                }
                if (item instanceof Artist) {
                    i.setAction("net.chrislehmann.squeezedroid.action.BrowseAlbum");
                    i.setData(Uri.parse("squeeze:///artist/" + item.getId()));
                    startActivityForResult(i, SqueezeDroidConstants.RequestCodes.REQUEST_BROWSE);
                    handled = true;
                }
            }
            return handled;
        }
    };

    OnClickListener onSearchButtonClicked = new OnClickListener() {
        SqueezeServiceAwareThread doSearch = new SqueezeServiceAwareThread() {
            public void runWithService(SqueezeService service) {
                String searchTerms = searchCriteriaText.getText().toString();
                if (StringUtils.isNotBlank(searchTerms)) {
                    final SearchResult result = service.search(searchTerms, 50);
                    if (result != null) {
                        adapter.setResult(result);
                        runOnUiThread(new Runnable() {
                            public void run() {
                                searchInstructionsText.setVisibility(View.INVISIBLE);
                                if (result.getTotalResults() == 0) {
                                    resultsExpandableListView.setVisibility(View.INVISIBLE);
                                    noResultsFoundText.setVisibility(View.VISIBLE);
                                } else {
                                    resultsExpandableListView.setVisibility(View.VISIBLE);
                                    noResultsFoundText.setVisibility(View.INVISIBLE);
                                }
                                adapter.notifyDataSetChanged();
                                hideKeyboard();
                            }
                        });
                    }
                }

            }
        };

        public void onClick(View v) {

            runWithService(doSearch);

        }
    };

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(searchCriteriaText.getWindowToken(), 0);
    }

    @Override
    public boolean onContextItemSelected(final MenuItem item) {
        boolean handled = true;
        ExpandableListContextMenuInfo menuInfo = (ExpandableListContextMenuInfo) item.getMenuInfo();
        final int group = ExpandableListView.getPackedPositionGroup(menuInfo.packedPosition);
        final int child = ExpandableListView.getPackedPositionChild(menuInfo.packedPosition);


        runWithService(new SqueezeServiceAwareThread() {
            public void runWithService(SqueezeService service) {
                Item selectedItem = null;
                if (child >= 0) {
                    selectedItem = (Item) resultsExpandableListView.getExpandableListAdapter().getChild(group, child);
                }

                final StringBuffer message = new StringBuffer("");
                if (selectedItem != null) {
                    switch (item.getItemId()) {
                        case CONTEXTMENU_ADD_ITEM:
                            service.addItem(getSelectedPlayer(), selectedItem);
                            message.append("Added to playlist");
                            break;
                        case CONTEXTMENU_PLAY_ITEM:
                            service.playItem(getSelectedPlayer(), selectedItem);
                            message.append("Now Playing");
                            break;
                        case CONTEXTMENU_PLAY_NEXT:
                            service.playItemNext(getSelectedPlayer(), selectedItem);
                            message.append("Playing next.");
                            break;
                    }
                }

                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(context, message.toString(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });


        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_itemlist, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean handled = true;


        switch (item.getItemId()) {
            case R.id.menuItem_itemlistDone:
                setResult(SqueezeDroidConstants.ResultCodes.RESULT_DONE);
                finish();
                break;
            default:
                handled = false;
        }

        if (!handled) {
            handled = super.onOptionsItemSelected(item);
        }
        return handled;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == SqueezeDroidConstants.ResultCodes.RESULT_DONE) {
            setResult(SqueezeDroidConstants.ResultCodes.RESULT_DONE);
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

}