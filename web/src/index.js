import 'bootstrap/dist/css/bootstrap.css';
import 'bootstrap/dist/css/bootstrap-theme.css';
import './index.css';

import React from 'react';
import ReactDOM from 'react-dom';

import { Modal, Button } from 'react-bootstrap';

import $ from 'jquery';

const gapi = window.gapi;

const DEV = process.env.NODE_ENV !== "production";
const API_ROOT = DEV ? 'http://localhost:6543' : 'https://knapsack-api.quuux.org';
const CLIENT_ID = '843379878054-9vg4fq679tsmir2k0rf7hbuhk6n9f214.apps.googleusercontent.com';

class _API {
    constructor(api_root, client_id) {
        this.api_root = api_root;
        this.auth2 = null;
        this.currentUser = null;

        this.loadAuth(client_id);
    }

    loadAuth(client_id) {
        var api = this;
        gapi.load('auth2', () => {
            api.auth2 = gapi.auth2.init({
                client_id: client_id,
                scope: 'profile'
            });

            api.auth2.isSignedIn.listen(() => {
                attachApp();
            });

            api.auth2.currentUser.listen((user) => {
                api.currentUser = user;
            });

            if (api.auth2.isSignedIn.get()) {
                attachApp();
            }

        });
    }
    
    getAuth() {
        return this.currentUser.getAuthResponse(true).access_token;
    }
    buildXHR(method, path, data, success, error) {

        if (!error) {
            error = (xhr, msg) => {
                show_error("Unexpected Network Error", msg);
            }
        }
        
        return $.ajax({
            dataType: "json",
            method: method,
            url: this.api_root + path,
            headers: {
                Auth: this.getAuth()
            },
            data: data,
            success: success,
            error: error
        });        
    }
    buildXHRJson(method, path, data, success, error) {
        return this.buildXHR(method, path, JSON.stringify(data), success, error);
    }   
    getPages(before, success, error) {
        var params = {};
        if (before)
            params.before = before;
        return this.buildXHR("GET", "/pages", params, success, error);
    }
    addPage(url, success, error) {
        return this.buildXHRJson("POST", "/pages", {page: {url: url}}, success, error);        
    }
    deletePage(page, success, error) {
        return this.buildXHRJson("DELETE", "/pages", {uid: page.uid, url: page.url}, success, error);
    }
}

const API = new _API(API_ROOT, CLIENT_ID);

function hostname(url) {
    return new URL(url).hostname;
}

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


class ErrorModal extends React.Component {
    constructor(props) {
        super(props);
        this.state = {show: true};
        this.show = this.show.bind(this);
        this.close = this.close.bind(this);
    }

    show() {
        this.setState({show: true});
    }
    
    close() {
        this.setState({show: false});
    }

    render() {
        return (
        <div className="static-modal">
          <Modal show={this.state.show} onHide={this.close}>
            <Modal.Header closeButton>
              <Modal.Title>{this.props.title}</Modal.Title>
            </Modal.Header>
        
            <Modal.Body>
              {this.props.message}
            </Modal.Body>
        
            <Modal.Footer>
              <Button onClick={this.close}>Close</Button>
            </Modal.Footer>

          </Modal>
        </div>
    );
    }
}

function show_error(title, message) {
    ReactDOM.render(
        <ErrorModal title={title} message={message}/>,
        document.getElementById('modal-container')
    ).show();
}

class PageItem extends React.Component {
    constructor(props) {
        super(props);
        this.state = {hover: false};
        this.mouseEnter = this.mouseEnter.bind(this);
        this.mouseLeave = this.mouseLeave.bind(this);
        this.delete = this.delete.bind(this);
    }

    mouseEnter() {
        this.setState({hover: true});
    }

    mouseLeave() {
        this.setState({hover: false});
    }

    delete(e) {
        e.preventDefault();
        API.deletePage(this.props.page, (r) => {
            if (r.status === "ok") {
                this.props.onPageRemoved(this.props.page);
            } else {
                show_error("Error Deleting Page", r.message);
            }
        });
    }
    
    render() {
        var deleteElement = "";
        if (this.state.hover) {
            deleteElement = <a className="btn btn-xs btn-danger pull-right" href={"#" + this.props.page.uid} onClick={this.delete}>
                <span className="glyphicon glyphicon-trash"></span> Delete
            </a>;
        }

        var unreadBadge;
        if (!this.props.page.read) {
            unreadBadge = <span className="label label-info">Unread</span>;
        }
        
        var title, subtitle;
        if (this.props.page.title) {
            title = <div>{unreadBadge} <a href={this.props.page.url} target="_blank">{this.props.page.title}</a></div>;
            subtitle = <div><small className="text-muted">{hostname(this.props.page.url)}</small></div>;
        } else {
            title = <div><a href={this.props.page.url} target="_blank">{this.props.page.url}</a></div>;
        }
        
        return (
            <li id={this.props.page.uid} className="page-item" onMouseEnter={this.mouseEnter} onMouseLeave={this.mouseLeave}>
                {title}
                {deleteElement}
                {subtitle}
                <div><small className="text-muted">Saved on {formatDateTime(this.props.page.created)}</small></div>
            </li>
        );
    }
}

class PageList extends React.Component {
    constructor(props) {
        super(props);
        this.state = {pages: [], url: "", before: null};
        this.onPagesLoaded = this.onPagesLoaded.bind(this);
        this.onPageRemoved = this.onPageRemoved.bind(this);
        this.onInputChanged = this.onInputChanged.bind(this);
        this.handlePagesLoadedResponse = this.handlePagesLoadedResponse.bind(this);
        this.loadMore = this.loadMore.bind(this);
        this.addPage = this.addPage.bind(this);
    }

    handlePagesLoadedResponse(r) {
        if (r.status === "ok") {
            var before = r.before ? r.before : null;
            this.onPagesLoaded(r.pages, before);
        } else {
            show_error("Error Loading Pages", r.message)
        }
    }
    
    onPagesLoaded(pages, before) {
        this.setState({pages: this.state.pages.concat(pages), before: before});
    }


    onInputChanged(e) {
        this.setState({url: e.target.value});
    }
    
    addPage(e) {
        e.preventDefault();
        
        var url = this.state.url;
        if (!url) {
            window.alert("missing url");
            return;
        }

        API.addPage(url, (r) => {
            if (r.status === "ok") {
                this.state.pages.unshift(r.page);
                this.setState({pages: this.state.pages, url: ""});
            } else {
                show_error("Error Adding Page", r.message);
            }
        });      
    }

    componentDidMount() {
        API.getPages(null, this.handlePagesLoadedResponse);
    }

    onPageRemoved(page) {
        var pages = this.state.pages.filter((p) => {
            return p.uid !== page.uid;
        });
        
        this.setState({pages: pages});
    }

    loadMore(e) {
        e.preventDefault();
        
        if (!this.state.before)
            return;
        API.getPages(this.state.before, this.handlePagesLoadedResponse);
    }
    
    render() {
        var items = this.state.pages.map((page) => <PageItem  page={page} key={page.uid} onPageRemoved={this.onPageRemoved}/>);

        var more;
        if (this.state.before) {
            var url = API_ROOT + "/pages?before=" + this.state.before;
            more = <a href={url} onClick={this.loadMore}>(more)</a>
        }
        
        return (
             <div className="row">               
                <div className="add-page">
                  <form onSubmit={this.addPage}>
                    <div className="input-group">
                      <input type="text"
                        className="form-control"
                        placeholder="Page URL (http://example.com/foo/bar)"
                        onChange={this.onInputChanged} value={this.state.url}/>    
                      <span className="input-group-btn">
                        <button className="btn btn-default" type="submit">Add</button>
                      </span>
                    </div>               
                </form>
                </div>                
               <ul className="pages">
                   {items}
               </ul>
               {more} 
            </div>
        );
    }
}

function attachApp() {
    ReactDOM.render(
        <PageList/>,
        document.getElementById('root')
    );
}
