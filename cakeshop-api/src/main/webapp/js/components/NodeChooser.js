import React, {Component} from "react";
import Select from "react-select";

const MANAGE_NODES_OPTION = {label: "Manage Nodes...", value: "manage"};

export default class NodeChooser extends Component {

    constructor(props, context) {
        super(props, context);
        this.state = {nodes: [], selectedNode: MANAGE_NODES_OPTION}
    }

    render() {
        const {nodes, selectedNode} = this.state;

        const nodesWithManageOption = [
            ...nodes,
            MANAGE_NODES_OPTION
        ];
        return <Select options={nodesWithManageOption}
                       onChange={this.onChange}
                       value={selectedNode}
                       placeholder={"Select..."}
        />;
    }

    componentDidMount() {
        const _this = this;
        Client.get('api/node/nodes')
        .done(function (response) {
            _this.setState((prevState) => {
                let selectedNode = prevState.selectedNode;
                let nodes = response.data.attributes.result.map((node) => {
                    const option = {
                        label: node.name,
                        value: node
                    };

                    if(node.isSelected) {
                        selectedNode = option;
                    }

                    return option;
                });

                if (selectedNode !== MANAGE_NODES_OPTION) {
                    _this.setFocusChangeListener();
                }

                return {...prevState, nodes, selectedNode}
            })
        })
    }

    setFocusChangeListener() {
        let _this = this;
        $(window).on("focus", function () {
            Client.get('api/node/currentUrl')
            .done(function (response) {
                let currentUrl = response.data.attributes.result;
                if (currentUrl !== _this.state.selectedNode.value.rpcUrl) {
                    console.log("Current url doesn't match dropdown, reloading",
                        currentUrl, _this.state.selectedNode.value.rpcUrl);
                    window.location.reload();
                }
            })
        })
    }

    onChange = (option, action, other) => {
        if (option.value === "manage") {
            window.location = "manage.html";
            return;
        }

        let node = option.value;
        Client.post('api/node/url', node)
        .done(function (response) {
            console.log("success", response);
            window.location.reload();
        })
        .fail(function (response) {
            console.log("fail", response);
        });
    }
}
