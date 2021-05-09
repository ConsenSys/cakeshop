import React, { Component } from 'react'
import { DragAndDropButton } from './DragAndDropButton'
import { AddNodeDialog } from './AddNodeDialog'
import { NodeGrid } from './NodeGrid'
import ArrowBack from '@material-ui/icons/ArrowBack'
import AppBar from '@material-ui/core/AppBar'
import Toolbar from '@material-ui/core/Toolbar'
import IconButton from '@material-ui/core/IconButton'
import Typography from '@material-ui/core/Typography'
import Container from '@material-ui/core/Container'

export default class Manage extends Component {
    constructor(props) {
        super(props);

        this.state = {
            addValue: "",
            nodes: [],
            dialogOpen: false
        };
    }

    componentDidMount() {
        this.fetchNodes()
    }

    fetchNodes () {
        let _this = this

        fetch('api/node/nodes', {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json'
            },
        })
        .then((response => {return response.json()}))
        .then(function (response) {
            let nodes = response.data.attributes.result
            console.log('nodes:', nodes)
            _this.setState({ nodes })
        })
    }

    render() {
        const {nodes, dialogOpen} = this.state;
        return (
            <div>
                <AppBar position="fixed" color="inherit">
                    <Toolbar>
                        <IconButton edge="start" aria-label="back"
                                    onClick={() => history.back()}>
                            <ArrowBack/>
                        </IconButton>
                        <Typography variant="h6"
                                    style={{marginLeft: 12, flex: 1}}>
                            Nodes
                        </Typography>
                        <DragAndDropButton
                            onClick={this.onAddNodeClick}
                            onConfigLoaded={this.onNodesJsonLoaded}/>
                    </Toolbar>
                </AppBar>
                <Container style={{marginTop: 84}}>
                    <NodeGrid list={nodes} onView={this.onView}
                              onDismiss={this.onDismiss}/>
                </Container>
                <AddNodeDialog open={dialogOpen}
                               onSubmit={this.onSubmit}
                               onCancel={this.onCancel}/>
            </div>
        )
    }

    onAddNodeClick = (e) => {
        e.stopPropagation();
        this.setState({dialogOpen: true})
    };

    onNodesJsonLoaded = async (newNodes) => {
        console.log('Nodes successfully loaded:', newNodes)
        if (newNodes != null && newNodes.length > 0) {
            const response = await fetch('api/node/addAll', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(newNodes)
            })
            console.log('add response:', response)
            if (response.status === 201) {
                console.log('Success:', response.status, response.statusText)
                this.fetchNodes()
            } else {
                console.log('Error:', response.status, response.statusText)
            }
        }
    };

    onCancel = () => {
        this.setState({dialogOpen: false})
    };

    onSubmit = async (newNode) => {
        this.setState({dialogOpen: false});
        const response = await fetch('api/node/add', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(newNode)
        })
        if (response.status === 201) {
            this.fetchNodes()
        } else {
            console.log('Error:', response.status, response.statusText)
        }
    };

    onView = (node) => {
        fetch("api/node/url", {
            method: "POST",
            mode: 'cors',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(node)
        }).then(response => {
            if (response.status === 200) {
                console.log("Success:", response.status, response.statusText);
                var loc = window.location.pathname;
                var dir = loc.substring(0, loc.lastIndexOf('/'));
                window.location = dir + "/";
            } else {
                console.log("Error:", response.status, response.statusText);
            }
        })
    };

    onDismiss = async (nodeToRemove) => {
        const response = await fetch('api/node/remove', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(nodeToRemove)
        })
        if (response.status === 204) {
            this.setState((prevState) => {
                const { nodes } = prevState
                let newNodes = nodes.filter((node) => node !== nodeToRemove)
                return {
                    ...prevState,
                    nodes: newNodes
                }
            })
        } else {
            console.log('Error:', response.status, response.statusText)
        }
    }
}
