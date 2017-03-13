PAGES_ENDPOINT = 'https://knapsack-api.quuux.org/pages';

function formatDateTime(dt) {
    var options = {
        hour12: true,
        year: "numeric",
        day: "numeric",
        weekday: "short",
        month: "short",
        hour: "numeric",
        minute: "numeric"
    };
    return new Date(dt).toLocaleDateString("en-US", options);
}

function getAuthToken(callback) {
    chrome.identity.getAuthToken({interactive: true}, callback);
}

function replace(fmt) {
    var params = arguments;

    function replacer(str, position, offset, s) {
        return String(params[Number(position)+1] || '');
    }

    return fmt.replace(/{(\d+)}/g, replacer);
}

function doRequest(method, url, params, callback, authToken) {
    var xhr = new XMLHttpRequest();

    xhr.open(method, url, true);

    xhr.onreadystatechange = function() {
        if (xhr.readyState == 4) {
            var resp = JSON.parse(xhr.responseText);
            if (callback) {
                callback(resp);
            }
        }
    };

    xhr.setRequestHeader('Auth', authToken);

    if (params) {
        xhr.setRequestHeader('Content-type', 'application/json');
        xhr.send(JSON.stringify(params));
    } else {
        xhr.send();
    }
}

function request(method, url, params, callback) {
    getAuthToken(function (token) {
        doRequest(method, url, params, callback, token);
    });
}

var get = function(url, callback) { return request('GET', url, null, callback); };
var post = function(url, params, callback) { return request('POST', url, params, callback); };
var put = function(url, params, callback) { return request('PUT', url, params, callback); };
var delete_ = function(url, params, callback) { return request('DELETE', url, params, callback); };

var Page = Backbone.Model.extend({
    idAttribute: 'url'
});

var PagesCollection = Backbone.Collection.extend({
    model: Page
});

var PageView = Backbone.View.extend({

    events: {
        'click .delete': 'onDelete',
        'click .title': 'open'
    },

    initialize: function() {
        _.bindAll(this, 'onDelete', 'onDeleted');
        this.template = $('#page-row-template').html();
    },

    render: function() {
        var title = this.model.get('title') || this.model.get('url');
        var created = formatDateTime(this.model.get('created'));
        this.$el.html(replace(this.template, title, created));
        return this;
    },

    open: function(e) {
        e.preventDefault();
        chrome.tabs.create({ url: this.model.get('url') });
    },

    onDelete: function(e) {
        e.preventDefault();

        $(e.target).spin('small');

        var page = this.model;
        var view = this;
        var params = {
            uid: page.get('uid'),
            url: page.get('url')
        };
        delete_(PAGES_ENDPOINT, params, view.onDeleted);
    },

    onDeleted: function() {
        this.$el.remove();
        this.trigger('delete', this.model);
    }
});

var PagesView = Backbone.View.extend({
    initialize: function() {
        this.listenTo(this.collection, 'add reset', this.render);
    },

    render: function() {
        this.$el.html('');

        this.collection.each(function(page) {
            var view = new PageView({model: page});
            view.render().$el.appendTo(this.$el);
            this.listenTo(view, 'delete', this.onPageDelete);
        }, this);
        return this;
    },

    onPageDelete: function(page) {
        this.collection.remove(page);
    }
});

var PopupView = Backbone.View.extend({

    events: {
        'click .add': 'onAdd',
        'click .install,.goto': 'openUrl'
    },

    initialize: function(options) {
        _.bindAll(this, 'onAdd', 'updateStatus');

        this.page = options.page;
        this.pages = new PagesCollection();

        this.listenTo(this.pages, 'reset', this.updateStatus);

        this.pagesView = new PagesView({
            el: this.$el.find('#pages').get(0),
            collection: this.pages
        });

        this.pagesView.render();

        this.loadPages();

        this.$('#url').val(this.page.get('url'));
    },

    setStatus: function(status) {
        this.$el.find('.add').html(status);
    },

    updateStatus: function() {
        if (this.pages.get(this.page.get('url'))) {
            this.setStatus('Added');
        }
    },

    onAdd: function(e) {
        e.preventDefault();

        var $spin = $(e.target);
        $spin.spin('small');

        this.setStatus('Adding...');

        var view = this;

        var params = {
            page: this.page.toJSON()
        };

        post(PAGES_ENDPOINT, params, function(resp) {
            $spin.spin(false);
            view.pageAdded(resp);
            view.setStatus(resp.status == 'ok' ? 'Added' : 'Error!');
        });
    },

    loadPages: function() {

        var $spin = this.$el.find('#pages');
        $spin.spin('small');

        var view = this;
        get(PAGES_ENDPOINT, function(resp) {
            $spin.spin(false);
            view.pagesLoaded(resp);
        });
    },

    pagesLoaded: function(resp) {
        this.pages.reset(resp.pages);
    },

    pageAdded: function(resp) {
        this.pages.add(resp.page, {at: 0});
    },
    
    openUrl: function(e) {
        e.preventDefault();
        chrome.tabs.create({ url: e.target.href });
    }
});

document.addEventListener('DOMContentLoaded', function() {

    chrome.tabs.query({active: true, currentWindow: true}, function(tabs) {

        var page = new Page({
            url: tabs[0].url
        });

        var view = new PopupView({
            el: document.body,
            page: page
        });

        view.render();
    });

});
