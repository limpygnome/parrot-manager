package com.limpygnome.parrot.component.ui;

import com.limpygnome.parrot.library.db.Database;
import com.limpygnome.parrot.library.db.DatabaseNode;
import javafx.event.EventHandler;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.web.WebView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.html.HTMLElement;

/**
 * Context menu for options of database nodes, when right-clicking web-view rendered by {@link WebViewStage}.
 */
public class ContextMenuHandler implements EventHandler<MouseEvent>
{
    private static final Logger LOG = LoggerFactory.getLogger(ContextMenuHandler.class);

    // The parent window/stage to which this belongs
    private WebViewStage webViewStage;

    // The current context menu; only instantiated when the context menu is open
    private ContextMenu contextMenu;

    public ContextMenuHandler(WebViewStage webViewStage)
    {
        this.webViewStage = webViewStage;
        attach();
    }

    /**
     * Attaches context menu to web view.
     */
    public void attach()
    {
        WebView webView = webViewStage.getWebView();

        // Disable default ctx menu
        webView.setContextMenuEnabled(false);

        // Hook mouse event
        webView.setOnMousePressed(this);
    }

    @Override
    public void handle(MouseEvent event)
    {
        WebView webView = webViewStage.getWebView();

        // Hide/destroy previous menu
        if (contextMenu != null)
        {
            contextMenu.hide();
            contextMenu = null;
        }

        // Determine if to show ctx menu
        if (event.getButton() == MouseButton.SECONDARY)
        {
            // Fetch element clicked
            String injectedScript = "document.elementFromPoint(" + event.getX() + "," + event.getY() + ");";
            netscape.javascript.JSObject object = (netscape.javascript.JSObject) webView.getEngine().executeScript(injectedScript);
            HTMLElement element = (HTMLElement) object;
            String selectedNodeId = element.getAttribute("id");

            if (selectedNodeId != null)
            {
                // Strip out _anchor from jstree
                if (selectedNodeId.endsWith("_anchor"))
                {
                    selectedNodeId = selectedNodeId.substring(0, selectedNodeId.length() - 7);
                }

                LOG.debug("ctx menu - node selected - id: {}", selectedNodeId);

                // Fetch database node
                Database database = webViewStage.getServiceFacade().getDatabaseService().getDatabase();

                if (database != null)
                {
                    DatabaseNode databaseNode = database.getNode(selectedNodeId);

                    if (databaseNode != null)
                    {
                        contextMenu = buildContextMenu(databaseNode);
                        contextMenu.show(webView, event.getScreenX(), event.getScreenY());

                        LOG.debug("showing context menu");
                    }
                    else
                    {
                        LOG.warn("unable to retrieve node - id: {}", selectedNodeId);
                    }
                }
                else
                {
                    LOG.warn("no database available, unable to retrieve database node - id: {}", selectedNodeId);
                }
            }
        }
    }

    private ContextMenu buildContextMenu(DatabaseNode databaseNode)
    {
        ContextMenu contextMenu = new ContextMenu();

        if (!databaseNode.isRoot())
        {
            MenuItem itemCopyClipboard = new MenuItem("Copy to clipboard");
            itemCopyClipboard.setOnAction(e ->
            {
                webViewStage.triggerEvent("document", "databaseClipboardEvent", databaseNode.getId());
            });

            contextMenu.getItems().addAll(itemCopyClipboard);
        }

        MenuItem itemAddEntry = new MenuItem("Add Entry");
        itemAddEntry.setOnAction(e -> {
            webViewStage.triggerEvent("document", "databaseEntryAdd", databaseNode.getId());
        });

        contextMenu.getItems().add(itemAddEntry);

        if (!databaseNode.isRoot())
        {
            MenuItem itemDeleteEntry = new MenuItem("Delete Entry");
            itemDeleteEntry.setOnAction(e ->
            {
                webViewStage.triggerEvent("document", "databaseEntryDelete", databaseNode.getId());
            });

            contextMenu.getItems().addAll(itemDeleteEntry);
        }

        // collapse/expand items
        SeparatorMenuItem collapseExpandSeparator = new SeparatorMenuItem();

        MenuItem itemExpand = new MenuItem("Expand");
        itemExpand.setOnAction(e ->
        {
            webViewStage.triggerEvent("document", "databaseEntryExpand", databaseNode.getId());
        });

        MenuItem itemExpandAll = new MenuItem("Expand All");
        itemExpandAll.setOnAction(e ->
        {
            webViewStage.triggerEvent("document", "databaseEntryExpandAll", databaseNode.getId());
        });

        MenuItem itemCollapse = new MenuItem("Collapse");
        itemCollapse.setOnAction(e ->
        {
            webViewStage.triggerEvent("document", "databaseEntryCollapse", databaseNode.getId());
        });

        MenuItem itemCollapseAll = new MenuItem("Collapse All");
        itemCollapseAll.setOnAction(e ->
        {
            webViewStage.triggerEvent("document", "databaseEntryCollapseAll", databaseNode.getId());
        });

        contextMenu.getItems().addAll(collapseExpandSeparator, itemExpand, itemExpandAll, itemCollapse, itemCollapseAll);

        return contextMenu;
    }

}
