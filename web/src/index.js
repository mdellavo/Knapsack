import 'bootstrap/dist/css/bootstrap.css';
import 'bootstrap/dist/css/bootstrap-theme.css';
import './index.css';

import React from 'react';
import ReactDOM from 'react-dom';

import { Modal, Button } from 'react-bootstrap';

import $ from 'jquery';

const gapi = window.gapi;

const DEV = true;
const API_ROOT = DEV ? 'http://localhost:6543' : 'https://knapsack.quuux.org';
const CLIENT_ID = '843379878054-9vg4fq679tsmir2k0rf7hbuhk6n9f214.apps.googleusercontent.com';

var auth2;
var currentUser;

class _API {
    getAuth() {
        return currentUser.getAuthResponse().id_token;
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
            url: API_ROOT + path,
            headers: {
                Auth: this.getAuth()
            },
            data: data ? JSON.stringify(data) : null,
            success: success,
            error: error
        });        
    }
    getPages(success, error) {
        return this.buildXHR("GET", "/pages", null, success, error);
    }
    addPage(url, success, error) {
        return this.buildXHR("POST", "/pages", {page: {url: url}}, success, error);        
    }
    deletePage(page, success, error) {
        return this.buildXHR("DELETE", "/pages", {uid: page.uid, url: page.url}, success, error);
    }
}

const API = new _API();

function hostname(url) {
    return new URL(url).hostname;
}

function formatDateTime(dt) {
    return new Date(dt).toLocaleDateString("en-US", {hour12: true, year: "numeric", day: "numeric", weekday: "short", month: "short", hour: "numeric", minute: "numeric"});
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
        this.state = {pages: [], url: ""};
        this.onPagesLoaded = this.onPagesLoaded.bind(this);
        this.onPageRemoved = this.onPageRemoved.bind(this);
        this.onInputChanged = this.onInputChanged.bind(this);
        this.addPage = this.addPage.bind(this);
    }

    onPagesLoaded(pages) {
        this.setState({pages: this.state.pages.concat(pages)});
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
        var list = this;
        API.getPages((r) => {
            if (r.status === "ok") {
                list.onPagesLoaded(r.pages);
            } else {
                show_error("Error Loading Pages", r.message)
            }
        });
    }

    onPageRemoved(page) {
        var pages = this.state.pages.filter((p) => {
            return p.uid !== page.uid;
        });
        
        this.setState({pages: pages});
    }
    
    render() {
        var items = this.state.pages.map((page) => <PageItem page={page} key={page.uid} onPageRemoved={this.onPageRemoved}/>);
        return (
             <div className="row">
                
                <div className="add-page">
                <form onSubmit={this.addPage}>

                <div className="input-group">

                <input type="text" className="form-control" placeholder="Page URL (http://example.com/foo/bar)" onChange={this.onInputChanged} value={this.state.url}/>
                
                <span className="input-group-btn">
                <button className="btn btn-default" type="submit">Add</button>
                </span>
                
                 </div>
                </form>
                </div>
                
               <ul className="pages">
                   {items}
               </ul>
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

gapi.load('auth2', function() {
    auth2 = gapi.auth2.init({
        client_id: CLIENT_ID,
        scope: 'profile'
    });

    auth2.isSignedIn.listen(function() {
        attachApp();
    });

    auth2.currentUser.listen(function(user) {
        currentUser = user;
    });

    if (auth2.isSignedIn.get()) {
        attachApp();
    }

});


